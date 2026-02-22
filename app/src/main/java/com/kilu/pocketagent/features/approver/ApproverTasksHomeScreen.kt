package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.ApproverTaskItem
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.kilu.pocketagent.shared.models.QuotasResp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun ApproverTasksHomeScreen(
    apiClient: ApiClient,
    onSessionInvalid: () -> Unit,
    onNewTaskClick: () -> Unit,
    onTaskClick: (String, String) -> Unit, // pass task_id and status
    onInboxClick: () -> Unit,
    onPairHub: () -> Unit
) {
    var tasks by remember { mutableStateOf<List<ApproverTaskItem>>(emptyList()) }
    var quotas by remember { mutableStateOf<QuotasResp?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    val loadQuotas = {
        scope.launch {
            try {
                val req = Request.Builder().url(apiClient.apiUrl("quotas")).get().build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                if (resp.isSuccessful) {
                    quotas = jsonParser.decodeFromString<QuotasResp>(resp.body?.string() ?: "")
                }
            } catch (e: Exception) { /* ignore quota fetch errors for now */ }
        }
    }

    val loadTasks = {
        scope.launch {
            isLoading = true
            errorMsg = null
            loadQuotas()
            try {
                val req = Request.Builder()
                    .url(apiClient.apiUrl("tasks?limit=10"))
                    .get()
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "[]"
                    tasks = jsonParser.decodeFromString<List<ApproverTaskItem>>(bodyStr)
                } else if (resp.code == 401 || resp.code == 403) {
                    onSessionInvalid()
                } else {
                    errorMsg = ErrorHandler.parseError(resp)
                }
            } catch(e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        loadTasks()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("My Tasks", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = loadTasks) {
                Text("🔄") // Refresh icon
            }
        }

        quotas?.let { q ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Credits: P:${q.planner_credits} / R:${q.report_credits}", style = MaterialTheme.typography.labelSmall)
                    Text("Today: ${q.calls_today}/${q.daily_limit}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onNewTaskClick) {
                Text("New Task")
            }
            OutlinedButton(onClick = onInboxClick) {
                Text("Inbox")
            }
            OutlinedButton(onClick = onPairHub) {
                Text("Pair Hub")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (errorMsg != null) {
            Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
        }
        
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tasks) { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onTaskClick(task.task_id, task.status) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("ID: ${task.task_id.take(8)}...", style = MaterialTheme.typography.titleMedium)
                            Text("Task: ${task.title ?: task.user_prompt ?: "Untitled"}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Status: ${task.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (task.status == "NEEDS_PLAN_APPROVAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )

                            if (task.status == "DONE") {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (task.final_report_status == "DONE") {
                                    Text("Report: ${task.final_report?.take(50)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                } else if (task.final_report_status == "SUMMARIZING") {
                                    Text("Generating Report...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val req = Request.Builder()
                                                        .url(apiClient.apiUrl("tasks/${task.task_id}/report/generate"))
                                                        .post("{}".toByteArray().toRequestBody("application/json".toMediaType()))
                                                        .build()
                                                    apiClient.client.newCall(req).execute()
                                                    loadTasks()
                                                } catch (e: Exception) { /* ignore */ }
                                            }
                                        },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text("Generate AI Report", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (tasks.isEmpty()) {
                    item {
                        Text("No tasks found. Create one to start!", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
