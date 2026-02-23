package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskScreen(apiClient: ApiClient, onCreated: (String) -> Unit, onCancel: () -> Unit) {
    var titleInput by remember { mutableStateOf("") }
    var promptInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Task") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Title
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Task title") },
                placeholder = { Text("e.g. Collect pricing from competitor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Prompt
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                label = { Text("What should the agent do?") },
                placeholder = { Text("e.g. Go to the pricing page and extract all plan names and prices") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(12.dp))

            // URL (optional, goes into inputs.base_url)
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Target URL (optional)") },
                placeholder = { Text("https://example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick templates
            Text("Quick templates:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            val templates = listOf(
                Triple("Collect & Report", "Navigate to the URL and extract the main content. Summarize key information.", "https://en.wikipedia.org/wiki/Headless_browser"),
                Triple("Check Pricing", "Find the pricing page and extract all plan names, prices, and features.", "https://example.com"),
                Triple("Test Error Handling", "Attempt to visit the URL. Report what happened.", "https://httpstat.us/403")
            )
            templates.forEach { (title, prompt, url) ->
                TextButton(
                    onClick = {
                        titleInput = title
                        promptInput = prompt
                        urlInput = url
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        "Error: $errorMsg",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (titleInput.isBlank()) {
                        errorMsg = "Title is required."
                        return@Button
                    }
                    if (promptInput.isBlank()) {
                        errorMsg = "Prompt is required."
                        return@Button
                    }
                    isSubmitting = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val inputs = if (urlInput.isNotBlank()) mapOf("base_url" to urlInput.trim()) else null
                            val reqPayload = CreateTaskReq(
                                title = titleInput.trim(),
                                user_prompt = promptInput.trim(),
                                executor_preference = "HUB_PREFERRED",
                                inputs = inputs
                            )
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
                            errorMsg = "Network Error: ${e.message}"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting && titleInput.isNotBlank() && promptInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSubmitting) "Creating…" else "Create Task")
            }
        }
    }
}
