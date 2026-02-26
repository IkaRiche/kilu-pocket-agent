package com.kilu.pocketagent.features.hub

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.hub.service.HubRuntimeService
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.shared.models.HubQueueResponse
import com.kilu.pocketagent.shared.models.HubQueueListResponse
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubDashboardScreen(
    apiClient: ApiClient,
    onSessionInvalid: () -> Unit,
    onResetPairing: () -> Unit = {},
    onSwitchRole: () -> Unit = {},
    onDiagnostics: () -> Unit = {}
) {
    var queue by remember { mutableStateOf<List<HubQueueResponse>>(emptyList()) }
    var isPolling by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }
    val context = LocalContext.current
    
    var isServiceEnabled by remember { mutableStateOf(false) }

    fun pollQueue() {
        scope.launch {
            isPolling = true
            errorMsg = null
            try {
                val req = Request.Builder()
                    .url(apiClient.apiUrl("hub/queue?max=5"))
                    .get()
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "{}"
                    val wrapper = jsonParser.decodeFromString<HubQueueListResponse>(bodyStr)
                    queue = wrapper.items
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

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Pairing?") },
            text = { Text("This will unpair this Hub device. You'll need to scan the Approver QR code again.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetPairing()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hub Dashboard") },
                actions = {
                    IconButton(onClick = { pollQueue() }, enabled = !isPolling) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Diagnostics") },
                                onClick = { showMenu = false; onDiagnostics() }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Pairing") },
                                onClick = { showMenu = false; showResetDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch Role") },
                                onClick = { showMenu = false; onSwitchRole() }
                            )
                        }
                    }
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
            // Always-On toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        "Error: $errorMsg",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(queue.size) { index ->
                    val task = queue[index]
                    HubTaskCard(task = task)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (queue.isEmpty() && !isPolling) {
                    item { 
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("⏳", style = MaterialTheme.typography.headlineLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Queue is empty", style = MaterialTheme.typography.titleMedium)
                            Text("Waiting for approved tasks…", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HubTaskCard(task: HubQueueResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Task: ${task.task_id.take(8)}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("URL: ${task.external_url ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Expires: ${task.expires_at ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
