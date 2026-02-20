package com.kilu.pocketagent.features.hub

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.hub.service.HubRuntimeService
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.HubQueueResponse
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request

@Composable
fun HubDashboardScreen(apiClient: ApiClient, onSessionInvalid: () -> Unit) {
    var queue by remember { mutableStateOf<List<HubQueueResponse>>(emptyList()) }
    var isPolling by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }
    val context = LocalContext.current
    
    // E1/E2: Toggle
    var isServiceEnabled by remember { mutableStateOf(false) } // In a complete app, track actual service state via Flow

    fun pollQueue() {
        scope.launch {
            isPolling = true
            errorMsg = null
            try {
                val req = Request.Builder()
                    .url("\${apiClient.getBaseUrl()}/v1/hub/queue?max=5")
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Hub Dashboard", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { pollQueue() }, enabled = !isPolling) {
                Text("\uD83D\uDD04")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Always-On Mode (24/7)", style = MaterialTheme.typography.titleMedium)
                    Text("Executes tasks silently in background", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = isServiceEnabled,
                    onCheckedChange = { checked ->
                        isServiceEnabled = checked
                        val action = if (checked) HubRuntimeService.ACTION_START else HubRuntimeService.ACTION_STOP
                        val intent = Intent(context, HubRuntimeService::class.java).apply { this.action = action }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && checked) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Active Queue", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (errorMsg != null) {
            Text("Error: \$errorMsg", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(queue) { task ->
                HubTaskCard(task = task)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (queue.isEmpty() && !isPolling) {
                item { Text("Queue is empty. Waiting for tasks...") }
            }
        }
    }
}

@Composable
fun HubTaskCard(task: HubQueueResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Task: \${task.task_id.take(8)}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("URL: \${task.external_url}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Lease Expires: \${task.expires_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
