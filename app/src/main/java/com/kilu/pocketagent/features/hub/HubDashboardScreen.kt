package com.kilu.pocketagent.features.hub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.HubQueueResponse
import com.kilu.pocketagent.shared.models.MintStepBatchReq
import com.kilu.pocketagent.shared.models.MintStepBatchResp
import com.kilu.pocketagent.shared.models.SubmitResultReq
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun HubDashboardScreen(apiClient: ApiClient, onSessionInvalid: () -> Unit) {
    var queue by remember { mutableStateOf<List<HubQueueResponse>>(emptyList()) }
    var isPolling by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }
    
    // B4. Local cache to prevent double assumptions packet
    val escalatedTasks = remember { mutableStateListOf<String>() }

    fun pollQueue() {
        scope.launch {
            isPolling = true
            errorMsg = null
            try {
                val req = Request.Builder()
                    .url("${apiClient.getBaseUrl()}/v1/hub/queue?max=5")
                    .get()
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "[]"
                    queue = jsonParser.decodeFromString<List<HubQueueResponse>>(bodyStr)
                } else if (resp.code == 401 || resp.code == 403) {
                    onSessionInvalid()
                } else {
                    errorMsg = ErrorHandler.parseError(resp)
                }
            } catch(e: Exception) {
                errorMsg = e.message
            } finally {
                isPolling = false
            }
        }
    }

    LaunchedEffect(Unit) { pollQueue() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hub Dashboard", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { pollQueue() }, enabled = !isPolling) {
                Text("\uD83D\uDD04")
            }
        }
        Text("Waiting for remote tasks...", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMsg != null) {
            Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(queue) { task ->
                HubTaskCard(
                    task = task, 
                    apiClient = apiClient, 
                    jsonParser = jsonParser, 
                    hasEscalated = escalatedTasks.contains(task.task_id),
                    onEscalate = { escalatedTasks.add(task.task_id) },
                    onCompleted = { pollQueue() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (queue.isEmpty() && !isPolling) {
                item { Text("Queue is empty. Active background leasing disabled for MVP v0.") }
            }
        }
    }
}

// C1: Hub UX states 
enum class HubTaskState {
    IDLE, RUNNING, WAITING_APPROVER, DONE, FAILED
}

@Composable
fun HubTaskCard(task: HubQueueResponse, apiClient: ApiClient, jsonParser: Json, hasEscalated: Boolean, onEscalate: () -> Unit, onCompleted: () -> Unit) {
    var taskState by remember { mutableStateOf(if (hasEscalated) HubTaskState.WAITING_APPROVER else HubTaskState.IDLE) }
    var execResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Task: ${task.task_id.take(8)}", style = MaterialTheme.typography.titleMedium)
                Text(taskState.name, color = when(taskState) {
                    HubTaskState.RUNNING -> MaterialTheme.colorScheme.primary
                    HubTaskState.WAITING_APPROVER -> MaterialTheme.colorScheme.error
                    HubTaskState.DONE -> MaterialTheme.colorScheme.secondary
                    HubTaskState.FAILED -> MaterialTheme.colorScheme.error
                    HubTaskState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("URL: ${task.external_url}", style = MaterialTheme.typography.bodyMedium)
            
            if (taskState == HubTaskState.IDLE || taskState == HubTaskState.RUNNING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Lease Expires: ${task.expires_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (execResult != null) {
                Text(execResult!!, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
            } 
            
            if (taskState == HubTaskState.IDLE) {
                Button(
                    onClick = {
                        taskState = HubTaskState.RUNNING
                        scope.launch(Dispatchers.IO) {
                            var executor: com.kilu.pocketagent.core.hub.web.WebViewExecutor? = null
                            try {
                                // 1. Mint Stub Batch (Still needed per protocol)
                                val mintReq = Request.Builder()
                                    .url("${apiClient.getBaseUrl()}/v1/grants/${task.grant_id}/mint-step-batch")
                                    .post(jsonParser.encodeToString(MintStepBatchReq(1)).toByteArray().toRequestBody("application/json".toMediaType()))
                                    .build()
                                
                                val mResp = apiClient.client.newCall(mintReq).execute()
                                
                                // B3. Halt on explicit mint failure explicitly before spinning up WebView 
                                if (!mResp.isSuccessful) {
                                    execResult = "Mint failed (${mResp.code}): ${ErrorHandler.parseError(mResp)}"
                                    taskState = HubTaskState.FAILED
                                    return@launch
                                }
                                
                                // 2. Init Headless WebView
                                executor = com.kilu.pocketagent.core.hub.web.WebViewExecutor(context)
                                executor.initialize()
                                
                                // 3. Load URL + Catch Native Network/Timeout Errors
                                val loadRes = executor.loadUrl(task.external_url, pageLoadTimeoutMs = 15000, domReadyTimeoutMs = 5000)
                                if (loadRes.isFailure) {
                                    val escalateMsg = loadRes.exceptionOrNull()?.message ?: "Unknown load error"
                                    if (!hasEscalated) {
                                        triggerAssumptions(apiClient, task.task_id, jsonParser, "page_load_failed", escalateMsg)
                                        execResult = "Escalated: Load Failed"
                                        onEscalate()
                                    } else {
                                        execResult = "Blocked: Load failed again natively. Waiting on Approver."
                                    }
                                    taskState = HubTaskState.WAITING_APPROVER
                                    return@launch
                                }

                                // 4. Evaluate Heuristics (Paywall/Captcha)
                                val heuristics = executor.evaluateJavascript(com.kilu.pocketagent.core.hub.web.WebExtractScripts.CHECK_HEURISTICS, jsEvalTimeoutMs = 3000)
                                if (heuristics.isNotEmpty() && heuristics != "null" && heuristics.isNotBlank()) {
                                    if (!hasEscalated) {
                                        triggerAssumptions(apiClient, task.task_id, jsonParser, "security_heuristic", heuristics)
                                        execResult = "Escalated: Security Check Triggered"
                                        onEscalate()
                                    } else {
                                        execResult = "Blocked: Security check persists. Waiting on Approver."
                                    }
                                    taskState = HubTaskState.WAITING_APPROVER
                                    return@launch
                                }

                                // 5. Safe Execute Extractions (Bounded by jsEvalTimeout)
                                val paragraphs = executor.evaluateJavascript(com.kilu.pocketagent.core.hub.web.WebExtractScripts.EXTRACT_PARAGRAPHS, jsEvalTimeoutMs = 3000)
                                val rawHeadingsStr = executor.evaluateJavascript(com.kilu.pocketagent.core.hub.web.WebExtractScripts.EXTRACT_HEADINGS, jsEvalTimeoutMs = 3000)
                                
                                // Cleanup JS output bounds
                                val parsedHeadings = try {
                                    jsonParser.decodeFromString<List<String>>(rawHeadingsStr)
                                } catch (_: Exception) { emptyList() }
                                
                                val safeText = paragraphs.replace("\\\\n", "\n").replace("\\\\\"", "\"").trim()

                                // A4. Extraction Guards
                                if (safeText.length < 200 && parsedHeadings.isEmpty()) {
                                    if (!hasEscalated) {
                                        triggerAssumptions(apiClient, task.task_id, jsonParser, "extraction_blocked", "DOM loaded but no meaningful paragraphs or headings extracted. Site may be an unsupported SPA or actively blocking bots.")
                                        execResult = "Escalated: Empty Extraction Guard Triggered"
                                        onEscalate()
                                    } else {
                                         execResult = "Blocked: Extraction still empty. Waiting on Approver."
                                    }
                                    taskState = HubTaskState.WAITING_APPROVER
                                    return@launch
                                }

                                // 6. Evidence Hashing
                                val textHashHex = com.kilu.pocketagent.core.crypto.DigestUtil.sha256Hex(safeText)
                                val headingsHashHex = com.kilu.pocketagent.core.crypto.DigestUtil.sha256Hex(parsedHeadings.joinToString("|"))

                                val hashObj = JsonObject(mapOf(
                                    "text_hash" to kotlinx.serialization.json.JsonPrimitive("sha256:$textHashHex"),
                                    "headings_hash" to kotlinx.serialization.json.JsonPrimitive("sha256:$headingsHashHex")
                                ))

                                // 7. Deterministic Report Builder
                                val autoSummary = "Successfully extracted ${safeText.length} characters and ${parsedHeadings.size} headings from ${task.external_url} via Headless Edge WebView execution."
                                val facts = parsedHeadings.take(5).map { "Heading Extracted: $it" }

                                // 8. Submit Final Result
                                val resPayload = SubmitResultReq(
                                    url = task.external_url,
                                    extracted_text = safeText,
                                    summary = autoSummary,
                                    headings = parsedHeadings,
                                    facts = facts,
                                    hashes = hashObj
                                )
                                
                                val pubReq = Request.Builder()
                                    .url("${apiClient.getBaseUrl()}/v1/tasks/${task.task_id}/result")
                                    .post(jsonParser.encodeToString(resPayload).toByteArray().toRequestBody("application/json".toMediaType()))
                                    .build()
                                    
                                val pubResp = apiClient.client.newCall(pubReq).execute()
                                
                                // B2: Idempotency check 
                                if (pubResp.isSuccessful || pubResp.code == 409) {
                                    execResult = "Execution Result Delivered!"
                                    taskState = HubTaskState.DONE
                                    withContext(Dispatchers.Main) { onCompleted() }
                                } else {
                                    execResult = "Submit Failed (${pubResp.code}): ${ErrorHandler.parseError(pubResp)}"
                                    taskState = HubTaskState.FAILED
                                }
                            } catch (e: Exception) {
                                execResult = "Crash: ${e.message}"
                                taskState = HubTaskState.FAILED
                            } finally {
                                executor?.destroy()
                                if (taskState == HubTaskState.RUNNING) {
                                    // Fallback to FAILED if unhandled interrupt
                                    taskState = HubTaskState.FAILED 
                                }
                            }
                        }
                        
                        // B1. Lease refresh heartbeat logic
                        scope.launch(Dispatchers.IO) {
                            while(taskState == HubTaskState.RUNNING) {
                                delay(30_000)
                                if (taskState == HubTaskState.RUNNING) {
                                    try {
                                        val req = Request.Builder()
                                            .url("${apiClient.getBaseUrl()}/v1/hub/lease/refresh")
                                            .post("{\"task_id\":\"${task.task_id}\"}".toRequestBody("application/json".toMediaType()))
                                            .build()
                                        apiClient.client.newCall(req).execute()
                                    } catch(e: Exception) { }
                                }
                            }
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run Extract Loop")
                }
            } else if (taskState == HubTaskState.WAITING_APPROVER) {
                Button(onClick = { /* Wait for polling sync */ }, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Escalated: Check Approver Inbox")
                }
            }
        }
    }
}

suspend fun triggerAssumptions(apiClient: ApiClient, taskId: String, json: Json, key: String, reason: String) {
    try {
        val payload = RequestAssumptionsReq(
            assumptions = listOf(
                com.kilu.pocketagent.shared.models.AssumptionItemReq(key, "Execution blocked by heuristic: \$reason. Proceed manually?")
            )
        )
        val req = Request.Builder()
            .url("\${apiClient.getBaseUrl()}/v1/tasks/\$taskId/assumptions/request")
            .post(json.encodeToString(payload).toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        apiClient.client.newCall(req).execute()
    } catch (e: Exception) {
        // Log or silently fail escalation bounds
    }
}
