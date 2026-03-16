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
import com.kilu.pocketagent.core.network.ControlPlaneApi
import com.kilu.pocketagent.shared.models.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class HubRuntimeLoop(private val context: Context, private val apiClient: ApiClient) {

    private val backoffPolicy = BackoffPolicy()
    private val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val wakelockGuard = WakelockGuard(context)
    private val controlPlane = ControlPlaneApi(apiClient.client, apiClient.apiUrl("")) {
        apiClient.store.clearSessionToken()
    }
    
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
                    transition(BackoffState.IDLE, "Hourly Limit (${MAX_RUNS_PER_HOUR}) Reached. Sleeping...")
                    delay(300_000L) // Sleep 5 mins and recheck
                    continue
                }

                transition(BackoffState.IDLE, "Polling Queue")
                val tasks = pollQueue()
                Log.d("HubRuntimeLoop", "Queue size=${tasks.size}")
                
                if (tasks.isEmpty()) {
                    transition(BackoffState.IDLE, "Queue empty")
                    delay(backoffPolicy.getDelayMs(BackoffState.IDLE))
                    continue
                }
                
                val task = tasks.first()
                if (assumptionsPrefs.getBoolean("esc_${task.task_id}", false)) {
                    transition(BackoffState.WAITING_APPROVER, "Waiting for Approver resolution on task ${task.task_id.take(8)}")
                    delay(backoffPolicy.getDelayMs(BackoffState.WAITING_APPROVER))
                    continue
                }
                
                // LEASED -> RUNNING
                executeTask(task, isActive)
                
            } catch (e: Exception) {
                Log.e("HubRuntimeLoop", "Fatal Loop Error: ${e.message}")
                transition(BackoffState.ERROR_UNKNOWN, "Crash: ${e.message}")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_UNKNOWN))
            }
        }
        wakelockGuard.release()
    }

    private suspend fun executeTask(task: HubQueueResponse, isActive: () -> Boolean) = coroutineScope {
        wakelockGuard.acquire(120_000)
        currentState = BackoffState.IDLE
        transition(BackoffState.IDLE, "Running task ${task.task_id.take(8)}")
        Log.d("HubRuntimeLoop", "Executing task=${task.task_id} url=${task.external_url}")
        runHistory.add(System.currentTimeMillis())
        
        var heartbeatJob: Job? = null
        var executor: WebViewExecutor? = null
        try {
            // Heartbeat
            heartbeatJob = launch(Dispatchers.IO) {
                while (isActive() && wakelockGuard.isHeld()) {
                    delay(30_000)
                    controlPlane.refreshLease(task.task_id)
                }
            }

            // Mint batch checks
            val runtimeId = apiClient.store.getDeviceId() ?: "android_unknown"
            val toolchainId = "tc_webview_v1"
            val stepId = "step_0"
            val stepDigest = DigestUtil.sha256Hex(task.external_url ?: "")
            
            val mintReq = MintStepBatchReq(
                runtime_id = runtimeId,
                toolchain_id = toolchainId,
                steps = listOf(StepInfo(stepId, stepDigest))
            )

            val startTime = java.time.Instant.now().toString()
            
            if (task.grant_id == null) {
                transition(BackoffState.ERROR_QUOTA, "No grant_id provided")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_QUOTA))
                return@coroutineScope
            }
            val mintResp = controlPlane.mintStepBatch(task.grant_id, mintReq)
            if (mintResp == null) {
                transition(BackoffState.ERROR_QUOTA, "Mint failed or exhausted")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_QUOTA))
                return@coroutineScope
            }

            // WebView
            executor = WebViewExecutor(context)
            executor.initialize()
            
            val loadRes = executor.loadUrl(task.external_url ?: "", 15000, 5000)
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
            
            val safeText = paragraphs.replace("\\n", "\n").replace("\\\"", "\"").trim()

            if (safeText.length < 200 && parsedHeadings.isEmpty()) {
                handleEscalation(task.task_id, "extraction_blocked", "DOM loaded but no meaningful content.")
                return@coroutineScope
            }

            // Build result
            val textHashHex = DigestUtil.sha256Hex(safeText)
            val headingsHashHex = DigestUtil.sha256Hex(parsedHeadings.joinToString("|"))

            val hashObj = JsonObject(mapOf(
                "text_hash" to JsonPrimitive("sha256:$textHashHex"),
                "headings_hash" to JsonPrimitive("sha256:$headingsHashHex")
            ))

            val autoSummary = "Successfully extracted ${safeText.length} characters and ${parsedHeadings.size} headings from ${task.external_url} via Headless Edge WebView execution."
            val facts = parsedHeadings.take(5).map { "Heading Extracted: $it" }

            val finishedTime = java.time.Instant.now().toString()
            val evidence = Evidence(
                task_id = task.task_id,
                step_id = stepId,
                runner_id = runtimeId,
                adapter = "webview",
                outcome = "success",
                started_at = startTime,
                finished_at = finishedTime,
                stdout_hash = "sha256:" + DigestUtil.sha256Hex(safeText),
                exit_code = 0
            )
            
            val ok = controlPlane.submitResult(task.task_id, SubmitResultReq(evidence))
            Log.d("HubRuntimeLoop", "Result submit ok=$ok task=${task.task_id}")
            if (ok) {
                transition(BackoffState.IDLE, "Success on task ${task.task_id.take(8)}")
                backoffPolicy.reset()
                
                // C3: Remove cache entry so we don't accidentally block it next time (idempotency prevents dupes server-side anyway)
                assumptionsPrefs.edit().remove("esc_${task.task_id}").apply()
            } else {
                transition(BackoffState.ERROR_NETWORK, "Submit Failed")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_NETWORK))
            }

        } finally {
            executor?.destroy()
            heartbeatJob?.cancel()
            wakelockGuard.release()
        }
    }

    private suspend fun handleEscalation(taskId: String, key: String, reason: String) {
        if (!assumptionsPrefs.getBoolean("esc_$taskId", false)) {
            val payload = RequestAssumptionsReq(
                assumptions = listOf(AssumptionItemReq(key, "Execution blocked: $reason"))
            )
            controlPlane.requestAssumptions(taskId, payload)
            
            assumptionsPrefs.edit().putBoolean("esc_$taskId", true).apply()
        }
        transition(BackoffState.WAITING_APPROVER, "Escalated to Approver")
        delay(backoffPolicy.getDelayMs(BackoffState.WAITING_APPROVER))
    }

    private suspend fun pollQueue(): List<HubQueueResponse> {
        return controlPlane.pollQueue(1)
    }

    private fun transition(state: BackoffState, msg: String) {
        currentState = state
        onStateChanged?.invoke(state, msg)
        Log.d("HubRuntimeLoop", "[$state] $msg")
    }
}
