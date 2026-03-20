package com.kilu.pocketagent.core.hub.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

// ... existing imports ...
import com.kilu.pocketagent.core.crypto.DigestUtil
import com.kilu.pocketagent.core.hub.web.HtmlFetcher
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

        // Fix 1: Register runtime on startup to ensure hub_runtimes.status=ONLINE
        // and last_seen_at is fresh. Required for eligible-runtime selection at task creation.
        val runtimeId = apiClient.store.getRuntimeId()
        val toolchainId = apiClient.store.getToolchainId()
        if (runtimeId != null && toolchainId != null) {
            val ok = controlPlane.registerRuntime(runtimeId, toolchainId)
            Log.d("HubRuntimeLoop", "Startup registerRuntime ok=$ok runtime=$runtimeId")
        } else {
            Log.w("HubRuntimeLoop", "runtime_id/toolchain_id missing from store — re-pair Hub to fix routing")
        }

        // Heartbeat: refresh presence every 5 minutes while loop is active
        val heartbeatScope = CoroutineScope(Dispatchers.IO)
        val heartbeatJob = heartbeatScope.launch {
            while (isActive()) {
                delay(5 * 60 * 1000L)
                if (runtimeId != null && toolchainId != null) {
                    val ok = controlPlane.registerRuntime(runtimeId, toolchainId)
                    Log.d("HubRuntimeLoop", "Periodic registerRuntime ok=$ok")
                }
            }
        }

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
                // Skip esc_ stale flag check — failed tasks are now submitted with outcome='failed'
                // and reach terminal DONE state on the server, so they drop from the queue naturally.
                
                // LEASED -> RUNNING
                executeTask(task, isActive)
                
            } catch (e: Exception) {
                Log.e("HubRuntimeLoop", "Fatal Loop Error: ${e.message}")
                transition(BackoffState.ERROR_UNKNOWN, "Crash: ${e.message}")
                delay(backoffPolicy.getDelayMs(BackoffState.ERROR_UNKNOWN))
            }
        }
        heartbeatJob.cancel()
        wakelockGuard.release()
    }


    private suspend fun executeTask(task: HubQueueResponse, isActive: () -> Boolean) = coroutineScope {
        wakelockGuard.acquire(120_000)
        currentState = BackoffState.IDLE
        transition(BackoffState.IDLE, "Running task ${task.task_id.take(8)}")
        Log.d("HubRuntimeLoop", "Executing task=${task.task_id} url=${task.external_url}")
        runHistory.add(System.currentTimeMillis())
        
        var heartbeatJob: Job? = null
        val htmlFetcher = HtmlFetcher()
        try {
            // Heartbeat
            heartbeatJob = launch(Dispatchers.IO) {
                while (isActive() && wakelockGuard.isHeld()) {
                    delay(30_000)
                    controlPlane.refreshLease(task.task_id)
                }
            }

            // Mint batch checks
            // Fix: use getRuntimeId() (rt_...) NOT getDeviceId() (dvc_...) — server checks runtime_id matches grant
            val runtimeId = apiClient.store.getRuntimeId() ?: apiClient.store.getDeviceId() ?: "android_unknown"
            val toolchainId = apiClient.store.getToolchainId() ?: "tc_android_v1"
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

            // Fetch & extract — with hard global timeout as safety net
            // OkHttp has 15s readTimeout but on some OEM Android, background threads
            // can be throttled. withTimeout(30s) guarantees we ALWAYS exit this block.
            val fetchResult = try {
                withTimeout(30_000L) {
                    htmlFetcher.fetchAndExtract(task.external_url ?: "")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // Timeout fired — submit FAILED result and exit
                val finishedTime = java.time.Instant.now().toString()
                val evidence = Evidence(
                    task_id = task.task_id, step_id = stepId, runner_id = runtimeId,
                    adapter = "okhttp", outcome = "failed",
                    started_at = startTime, finished_at = finishedTime,
                    stdout_hash = "sha256:" + DigestUtil.sha256Hex("fetch_timeout_30s"),
                    exit_code = 124
                )
                controlPlane.submitResult(task.task_id, SubmitResultReq(evidence))
                transition(BackoffState.IDLE, "Global 30s timeout on fetch for ${task.task_id.take(8)}")
                return@coroutineScope
            }

            if (fetchResult.error != null && fetchResult.error == "PAYWALL_DETECTED") {
                handleEscalation(task.task_id, "paywall", "Paywall detected by HtmlFetcher")
                return@coroutineScope
            }

            if (fetchResult.error != null || fetchResult.statusCode >= 400) {
                // HTTP error or network failure: submit FAILED result → terminal DONE
                val finishedTime = java.time.Instant.now().toString()
                val evidence = Evidence(
                    task_id = task.task_id,
                    step_id = stepId,
                    runner_id = runtimeId,
                    adapter = "okhttp",
                    outcome = "failed",
                    started_at = startTime,
                    finished_at = finishedTime,
                    stdout_hash = "sha256:" + DigestUtil.sha256Hex(fetchResult.error ?: "http_error"),
                    exit_code = if (fetchResult.statusCode >= 400) fetchResult.statusCode else 1
                )
                controlPlane.submitResult(task.task_id, SubmitResultReq(evidence))
                transition(BackoffState.IDLE, "Submit failed(${fetchResult.statusCode}) for ${task.task_id.take(8)}")
                return@coroutineScope
            }

            val safeText = fetchResult.paragraphs.trim()
            val parsedHeadings = fetchResult.headings
            val finalUrl = fetchResult.finalUrl  // canonical URL after redirects

            if (safeText.length < 100 && parsedHeadings.isEmpty()) {
                // Empty content: submit failed → terminal DONE
                val finishedTime = java.time.Instant.now().toString()
                val evidence = Evidence(
                    task_id = task.task_id,
                    step_id = stepId,
                    runner_id = runtimeId,
                    adapter = "okhttp",
                    outcome = "failed",
                    started_at = startTime,
                    finished_at = finishedTime,
                    stdout_hash = "sha256:" + DigestUtil.sha256Hex("empty_content"),
                    exit_code = 2
                )
                controlPlane.submitResult(task.task_id, SubmitResultReq(evidence))
                transition(BackoffState.IDLE, "Submit empty-content result for ${task.task_id.take(8)}")
                return@coroutineScope
            }

            // Build result
            val textHashHex = DigestUtil.sha256Hex(safeText)
            val headingsHashHex = DigestUtil.sha256Hex(parsedHeadings.joinToString("|"))

            val hashObj = JsonObject(mapOf(
                "text_hash" to JsonPrimitive("sha256:$textHashHex"),
                "headings_hash" to JsonPrimitive("sha256:$headingsHashHex")
            ))

            val autoSummary = "Fetched ${safeText.length} chars, ${parsedHeadings.size} headings from $finalUrl (requested: ${task.external_url}). Adapter: bounded OkHttp+Jsoup static HTML retrieval (no JS rendering)."
            val facts = parsedHeadings.take(5).map { "Heading Extracted: $it" }

            val finishedTime = java.time.Instant.now().toString()
            val evidence = Evidence(
                task_id = task.task_id,
                step_id = stepId,
                runner_id = runtimeId,
                adapter = "okhttp",
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
