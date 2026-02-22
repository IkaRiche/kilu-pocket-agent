package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.CreateTaskReq
import com.kilu.pocketagent.shared.models.CreateTaskResp
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun NewTaskScreen(apiClient: ApiClient, onCreated: (String) -> Unit, onCancel: () -> Unit) {
    var urlInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Task", style = MaterialTheme.typography.headlineMedium)
        Text("Collect & Report (Single URL)", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Target URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Report style is pinned to 'short' for MVP.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        // C4: Sample URLs for regression
        Text("Sample URLs for testing:", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        val sampleUrls = listOf(
            "https://en.wikipedia.org/wiki/Headless_browser",
            "https://example.com",
            "https://httpstat.us/403"
        )
        sampleUrls.forEach { purl ->
            TextButton(
                onClick = { urlInput = purl },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.defaultMinSize(minHeight = 24.dp)
            ) {
                Text(purl, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        if (errorMsg != null) {
            Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel, enabled = !isSubmitting) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (urlInput.isBlank()) {
                        errorMsg = "URL cannot be empty."
                        return@Button
                    }
                    isSubmitting = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val reqPayload = CreateTaskReq(url = urlInput.trim())
                            val req = Request.Builder()
                                .url(apiClient.apiUrl("tasks"))
                                .post(jsonParser.encodeToString(reqPayload).toByteArray().toRequestBody("application/json".toMediaType()))
                                .build()
                            
                            val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                            if (resp.isSuccessful) {
                                val bodyStr = resp.body?.string() ?: ""
                                val data = jsonParser.decodeFromString<CreateTaskResp>(bodyStr)
                                onCreated(data.task_id)
                            } else {
                                errorMsg = ErrorHandler.parseError(resp)
                            }
                        } catch (e: Exception) {
                            errorMsg = "Network Error: \${e.message}"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                Text(if (isSubmitting) "Creating..." else "Create Task")
            }
        }
    }
}
