package com.kilu.pocketagent.core.hub.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

// ... existing imports ...
import com.kilu.pocketagent.core.crypto.DigestUtil
import com.kilu.pocketagent.core.hub.web.WebExtractScripts
import com.kilu.pocketagent.core.hub.web.WebViewExecutor
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HubRuntimeLoop(private val context: Context, private val apiClient: ApiClient) {

    private val backoffPolicy = BackoffPolicy()
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val wakelockGuard = WakelockGuard(context)
    
    // C2: Ensure single-fire guard persists per task_id
    private val assumptionsPrefs = context.getSharedPreferences("kilu_assumptions_cache", Context.MODE_PRIVATE)

    @Volatile var currentState: BackoffState = BackoffState.IDLE
    
    var onStateChanged: ((BackoffState, String) -> Unit)? = null
    
    // D1: Max runs per hour limiter
    private val runHistory = mutableListOf<Long>()
    private val MAX_RUNS_PER_HOUR = 20
    
    // D2: Network checks
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun startLoop(isActive: () -> Boolean) {
        Log.d("HubRuntimeLoop", "Loop Started")
        while (isActive()) {
            try {
                if (apiClient.store.getSessionToken() == null) {
                    transition(BackoffState.ERROR_AUTH, "Missing Hub Session Token")
                    delay(backoffPolicy.getDelayMs(BackoffState.ERROR_AUTH))
                    continue
                }
                
                if (!isNetworkAvailable()) {
                    transition(BackoffState.ERROR_NETWORK, "No Network Connection")
                    delay(backoffPolicy.getDelayMs(BackoffState.ERROR_NETWORK))
                    continue
                }
                
                // Trim expire run history
                val now = System.currentTimeMillis()
                runHistory.removeAll { it < now - 3600_000L }
                if (runHistory.size >= MAX_RUNS_PER_HOUR) {
                    transition(BackoffState.IDLE, "Hourly Limit (\${MAX_RUNS_PER_HOUR}) Reached. Sleeping...")
                    delay(300_000L) // Sleep 5 mins and recheck
                    continue
                }

                transition(BackoffState.IDLE, "Polling Queue")
                val tasks = pollQueue()
                
                if (tasks.isEmpty()) {
                    transition(BackoffState.IDLE, "Queue empty")
                    delay(backoffPolicy.getDelayMs(BackoffState.IDLE))
                    continue
                }
                
                val task = tasks.first()
                if (assumptionsPrefs.getBoolean("esc_\${task.task_id}", false)) {
                    transition(BackoffState.WAITING_APPROVER, "Waiting for Approver resolution on task \${task.task_id.take(8)}")
                    delay(backoffPolicy.getDelayMs(BackoffState.WAITING_APPROVER))
                    continue
                }
                
                // LEASED -> RUNNING
                executeTask(task, isActive)
                
            } catch (e: Exception) {
                Log.e("HubRuntimeLoop", "Fatal Loop Error: \${e.message}")
                transition(BackoffState.ERROR_UNKNOWN, "Crash: \${e.message}")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_UNKNOWN))
            }
        }
        wakelockGuard.release()
    }

    private suspend fun executeTask(task: HubQueueResponse, isActive: () -> Boolean) = coroutineScope {
        wakelockGuard.acquire(120_000)
        currentState = BackoffState.IDLE // Visual RUNNING state logic
        transition(BackoffState.IDLE, "Running task \${task.task_id.take(8)}")
        runHistory.add(System.currentTimeMillis())
        
        var heartbeatJob: Job? = null
        var executor: WebViewExecutor? = null
        try {
            // Heartbeat
            heartbeatJob = launch(Dispatchers.IO) {
                while (isActive() && wakelockGuard.isHeld()) {
                    delay(30_000)
                    try {
                        val req = Request.Builder()
                            .url("\${apiClient.getBaseUrl()}/v1/hub/lease/refresh")
                            .post("{\\"task_id\\":\\"\${task.task_id}\\"}".toRequestBody("application/json".toMediaType()))
                            .build()
                        apiClient.client.newCall(req).execute()
                    } catch (e: Exception) { }
                }
            }

            // Mint batch checks
            val mintReq = Request.Builder()
                .url("\${apiClient.getBaseUrl()}/v1/grants/\${task.grant_id}/mint-step-batch")
                .post(jsonParser.encodeToString(MintStepBatchReq(1)).toByteArray().toRequestBody("application/json".toMediaType()))
                .build()
                                
            val mResp = withContext(Dispatchers.IO) { apiClient.client.newCall(mintReq).execute() }
            if (mResp.code == 403 || mResp.code == 429) {
                transition(BackoffState.ERROR_QUOTA, "Mint failed (\${mResp.code})")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_QUOTA))
                return@coroutineScope
            } else if (!mResp.isSuccessful) {
                transition(BackoffState.ERROR_NETWORK, "Mint network err (\${mResp.code})")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_NETWORK))
                return@coroutineScope
            }

            // WebView
            executor = WebViewExecutor(context)
            executor.initialize()
            
            val loadRes = executor.loadUrl(task.external_url, 15000, 5000)
            if (loadRes.isFailure) {
                handleEscalation(task.task_id, "page_load_failed", "Load failed natively")
                return@coroutineScope
            }

            val heuristics = executor.evaluateJavascript(WebExtractScripts.CHECK_HEURISTICS, 3000)
            if (heuristics.isNotEmpty() && heuristics != "null" && heuristics.isNotBlank()) {
                handleEscalation(task.task_id, "security_heuristic", heuristics)
                return@coroutineScope
            }

            val paragraphs = executor.evaluateJavascript(WebExtractScripts.EXTRACT_PARAGRAPHS, 3000)
            val rawHeadingsStr = executor.evaluateJavascript(WebExtractScripts.EXTRACT_HEADINGS, 3000)
            val parsedHeadings = try {
                jsonParser.decodeFromString<List<String>>(rawHeadingsStr)
            } catch (_: Exception) { emptyList() }
            
            val safeText = paragraphs.replace("\\\\n", "\n").replace("\\\\\\"", "\\"").trim()

            if (safeText.length < 200 && parsedHeadings.isEmpty()) {
                handleEscalation(task.task_id, "extraction_blocked", "DOM loaded but no meaningful content.")
                return@coroutineScope
            }

            // Build result
            val textHashHex = DigestUtil.sha256Hex(safeText)
            val headingsHashHex = DigestUtil.sha256Hex(parsedHeadings.joinToString("|"))

            val hashObj = JsonObject(mapOf(
                "text_hash" to JsonPrimitive("sha256:\$textHashHex"),
                "headings_hash" to JsonPrimitive("sha256:\$headingsHashHex")
            ))

            val autoSummary = "Successfully extracted \${safeText.length} characters and \${parsedHeadings.size} headings from \${task.external_url} via Headless Edge WebView execution."
            val facts = parsedHeadings.take(5).map { "Heading Extracted: \$it" }

            val resPayload = SubmitResultReq(
                url = task.external_url,
                extracted_text = safeText,
                summary = autoSummary,
                headings = parsedHeadings,
                facts = facts,
                hashes = hashObj
            )
            
            val pubReq = Request.Builder()
                .url("\${apiClient.getBaseUrl()}/v1/tasks/\${task.task_id}/result")
                .post(jsonParser.encodeToString(resPayload).toByteArray().toRequestBody("application/json".toMediaType()))
                .build()
                
            val pubResp = withContext(Dispatchers.IO) { apiClient.client.newCall(pubReq).execute() }
            if (pubResp.isSuccessful || pubResp.code == 409) {
                transition(BackoffState.IDLE, "Success on task \${task.task_id.take(8)}")
                backoffPolicy.reset()
                
                // C3: Remove cache entry so we don't accidentally block it next time (idempotency prevents dupes server-side anyway)
                assumptionsPrefs.edit().remove("esc_\${task.task_id}").apply()
            } else {
                transition(BackoffState.ERROR_NETWORK, "Submit Failed (\${pubResp.code})")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_NETWORK))
            }

        } finally {
            executor?.destroy()
            heartbeatJob?.cancel()
            wakelockGuard.release()
        }
    }

    private suspend fun handleEscalation(taskId: String, key: String, reason: String) {
        if (!assumptionsPrefs.getBoolean("esc_\$taskId", false)) {
            try {
                val payload = RequestAssumptionsReq(
                    assumptions = listOf(AssumptionItemReq(key, "Execution blocked: \$reason"))
                )
                val req = Request.Builder()
                    .url("\${apiClient.getBaseUrl()}/v1/tasks/\$taskId/assumptions/request")
                    .post(jsonParser.encodeToString(payload).toByteArray().toRequestBody("application/json".toMediaType()))
                    .build()
                withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
            } catch (e: Exception) { }
            
            assumptionsPrefs.edit().putBoolean("esc_\$taskId", true).apply()
        }
        transition(BackoffState.WAITING_APPROVER, "Escalated to Approver")
        delay(backoffPolicy.getDelayMs(BackoffState.WAITING_APPROVER))
    }

    private suspend fun pollQueue(): List<HubQueueResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("\${apiClient.getBaseUrl()}/v1/hub/queue?max=1")
                    .get()
                    .build()
                val resp = apiClient.client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "[]"
                    jsonParser.decodeFromString<List<HubQueueResponse>>(bodyStr)
                } else if (resp.code == 401 || resp.code == 403) {
                    apiClient.store.remove("sessionToken") // Force Error Auth
                    emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun transition(state: BackoffState, msg: String) {
        currentState = state
        onStateChanged?.invoke(state, msg)
        Log.d("HubRuntimeLoop", "[\$state] \$msg")
    }
}
