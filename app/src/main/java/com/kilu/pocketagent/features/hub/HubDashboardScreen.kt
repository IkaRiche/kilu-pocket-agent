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
    var statusText by remember { mutableStateOf<String?>(null) }
    var isPolling by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    fun pollQueue() {
        scope.launch {
            isPolling = true
            statusText = "Polling /v1/hub/queue..."
            try {
                val req = Request.Builder()
                    .url("\${apiClient.getBaseUrl()}/v1/hub/queue?max=5")
                    .get()
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "[]"
                    queue = jsonParser.decodeFromString<List<HubQueueResponse>>(bodyStr)
                    statusText = "Found \${queue.size} tasks in queue."
                } else if (resp.code == 401 || resp.code == 403) {
                    statusText = "Session generic failure. Re-pairing needed."
                    onSessionInvalid()
                } else if (resp.code == 429) {
                    statusText = "Rate limited, retry later."
                } else {
                    statusText = "Poll Error: \${ErrorHandler.parseError(resp)}"
                }
            } catch(e: Exception) {
                statusText = "Network Error: \${e.message}"
            } finally {
                isPolling = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Hub Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = { pollQueue() }, enabled = !isPolling, modifier = Modifier.fillMaxWidth()) {
            Text(if (isPolling) "Polling..." else "Poll Queue")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        if (statusText != null) {
            Text(statusText!!, color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(queue) { task ->
                HubTaskCard(task, apiClient, jsonParser) {
                    // Refresh after stub run
                    pollQueue()
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (queue.isEmpty() && !isPolling) {
                item {
                    Text("No tasks currently leased.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun HubTaskCard(task: HubQueueResponse, apiClient: ApiClient, jsonParser: Json, onCompleted: () -> Unit) {
    var isExecuting by remember { mutableStateOf(false) }
    var execResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Task: \${task.task_id}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("URL: \${task.external_url}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Lease Expires: \${task.expires_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (execResult != null) {
                Text(execResult!!, color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        isExecuting = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                // 1. Mint Stub Batch (Still needed per protocol)
                                val mintReq = Request.Builder()
                                    .url("\${apiClient.getBaseUrl()}/v1/grants/\${task.grant_id}/mint-step-batch")
                                    .post(jsonParser.encodeToString(MintStepBatchReq(1)).toByteArray().toRequestBody("application/json".toMediaType()))
                                    .build()
                                
                                val mResp = apiClient.client.newCall(mintReq).execute()
                                if (!mResp.isSuccessful) {
                                    execResult = "Mint failed: \${ErrorHandler.parseError(mResp)}"
                                    return@launch
                                }
                                
                                // 2. Init Headless WebView
                                val executor = com.kilu.pocketagent.core.hub.web.WebViewExecutor(context)
                                executor.initialize()
                                
                                // 3. Load URL + Catch Native Network/Timeout Errors
                                val loadRes = executor.loadUrl(task.external_url, timeoutMs = 20000)
                                if (loadRes.isFailure) {
                                    val escalateMsg = loadRes.exceptionOrNull()?.message ?: "Unknown load error"
                                    triggerAssumptions(apiClient, task.task_id, jsonParser, "page_load_failed", escalateMsg)
                                    execResult = "Escalated: Load Failed"
                                    onCompleted()
                                    return@launch
                                }

                                // 4. Evaluate Heuristics (Paywall/Captcha)
                                val heuristics = executor.evaluateJavascript(com.kilu.pocketagent.core.hub.web.WebExtractScripts.CHECK_HEURISTICS)
                                if (heuristics.isNotEmpty() && heuristics != "null" && heuristics.isNotBlank()) {
                                    triggerAssumptions(apiClient, task.task_id, jsonParser, "security_heuristic", heuristics)
                                    execResult = "Escalated: Security Check Triggered"
                                    onCompleted()
                                    return@launch
                                }

                                // 5. Safe Execute Extractions
                                val paragraphs = executor.evaluateJavascript(com.kilu.pocketagent.core.hub.web.WebExtractScripts.EXTRACT_PARAGRAPHS)
                                val rawHeadingsStr = executor.evaluateJavascript(com.kilu.pocketagent.core.hub.web.WebExtractScripts.EXTRACT_HEADINGS)
                                
                                // Cleanup JS output bounds
                                val parsedHeadings = try {
                                    jsonParser.decodeFromString<List<String>>(rawHeadingsStr)
                                } catch (_: Exception) { emptyList() }
                                
                                // Clean up the extracted text properly to avoid raw eval escaping artifacts
                                val safeText = paragraphs.replace("\\\\n", "\n").replace("\\\\\"", "\"").trim()

                                // 6. Evidence Hashing
                                val textHashHex = com.kilu.pocketagent.core.crypto.DigestUtil.sha256Hex(safeText)
                                val headingsHashHex = com.kilu.pocketagent.core.crypto.DigestUtil.sha256Hex(parsedHeadings.joinToString("|"))

                                val hashObj = JsonObject(mapOf(
                                    "text_hash" to kotlinx.serialization.json.JsonPrimitive("sha256:\$textHashHex"),
                                    "headings_hash" to kotlinx.serialization.json.JsonPrimitive("sha256:\$headingsHashHex")
                                ))

                                // 7. Deterministic Report Builder
                                val autoSummary = "Successfully extracted \${safeText.length} characters and \${parsedHeadings.size} headings from \${task.external_url} via Headless Edge WebView execution."
                                val facts = parsedHeadings.take(5).map { "Heading Extracted: \$it" }

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
                                    .url("\${apiClient.getBaseUrl()}/v1/tasks/\${task.task_id}/result")
                                    .post(jsonParser.encodeToString(resPayload).toByteArray().toRequestBody("application/json".toMediaType()))
                                    .build()
                                    
                                val pubResp = apiClient.client.newCall(pubReq).execute()
                                if (pubResp.isSuccessful) {
                                    execResult = "Execution Result Delivered!"
                                    onCompleted()
                                } else {
                                    execResult = "Submit Failed: \${ErrorHandler.parseError(pubResp)}"
                                }
                            } catch (e: Exception) {
                                execResult = "Crash: \${e.message}"
                            } finally {
                                isExecuting = false
                            }
                        }
                    }, 
                    enabled = !isExecuting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isExecuting) "Running..." else "Run Extract Loop")
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
