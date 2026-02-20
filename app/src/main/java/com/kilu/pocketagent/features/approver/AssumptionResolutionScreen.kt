package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.AssumptionItemReq
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class ResolutionItemReq(val key: String, val resolution: String)

@Serializable
data class ResolveAssumptionsReq(val resolutions: List<ResolutionItemReq>)

@Composable
fun AssumptionResolutionScreen(
    taskId: String,
    apiClient: ApiClient,
    onResolved: () -> Unit,
    onBack: () -> Unit
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    
    // For MVP we assume we are just unblocking and telling the Hub to bypass or retry.
    // Real flow would fetch the task's active assumptions from `/v1/tasks/{id}/assumptions`.
    // Since our Hub just submits `page_load_failed` or `security_heuristic`, we'll display a generic form.
    var resolutionText by remember { mutableStateOf("Bypass warning and retry extraction.") }
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Resolve Escalation", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Task: \${taskId.take(8)}...", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("The Hub execution node triggered a security heuristic or page timeout.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Please provide a resolution directive to unblock the execution loop.", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Select resolution directive:", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        // C2. 3-Button Action Array
        val actions = listOf(
            "Proceed (Bypass Warning)", 
            "Try Alternative Page", 
            "Stop Execution (Fail Task)"
        )
        actions.forEach { actionLabel ->
            OutlinedButton(
                onClick = { resolutionText = actionLabel },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (resolutionText == actionLabel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Text(actionLabel)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (errorMsg != null) {
            Text("Error: \$errorMsg", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, enabled = !isResolving) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    isResolving = true
                    errorMsg = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val payload = ResolveAssumptionsReq(
                                resolutions = listOf(
                                    ResolutionItemReq(key = "security_heuristic", resolution = resolutionText),
                                    ResolutionItemReq(key = "page_load_failed", resolution = resolutionText)
                                )
                            )
                            val req = Request.Builder()
                                .url("\${apiClient.getBaseUrl()}/v1/tasks/\$taskId/assumptions/resolve")
                                .post(jsonParser.encodeToString(payload).toByteArray().toRequestBody("application/json".toMediaType()))
                                .build()
                            val resp = apiClient.client.newCall(req).execute()
                            
                            withContext(Dispatchers.Main) {
                                if (resp.isSuccessful) {
                                    onResolved()
                                } else {
                                    errorMsg = ErrorHandler.parseError(resp)
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorMsg = "Network Error: \${e.message}"
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                isResolving = false
                            }
                        }
                    }
                },
                enabled = !isResolving && resolutionText.isNotBlank()
            ) {
                Text(if (isResolving) "Resolving..." else "Resolve & Resume")
            }
        }
    }
}
