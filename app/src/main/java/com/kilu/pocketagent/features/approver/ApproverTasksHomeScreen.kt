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
import okhttp3.Request

@Composable
fun ApproverTasksHomeScreen(
    apiClient: ApiClient,
    onSessionInvalid: () -> Unit,
    onNewTaskClick: () -> Unit,
    onTaskClick: (String, String) -> Unit, // pass task_id and status
    onInboxClick: () -> Unit
) {
    var tasks by remember { mutableStateOf<List<ApproverTaskItem>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    val loadTasks = {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val req = Request.Builder()
                    .url("${apiClient.getBaseUrl()}/v1/tasks?limit=10")
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
                Text("\uD83D\uDD04") // Refresh icon
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
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (errorMsg != null) {
            Text("Error: \$errorMsg", color = MaterialTheme.colorScheme.error)
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
                            Text("ID: \${task.task_id.take(8)}...", style = MaterialTheme.typography.titleMedium)
                            Text("URL: \${task.external_url}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Status: \${task.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (task.status == "NEEDS_PLAN_APPROVAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
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
