package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.ui.theme.StatusApproved
import com.kilu.pocketagent.core.ui.theme.StatusFailed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

@Serializable
data class DeviceInfo(
    val device_id: String,
    val device_type: String,
    val display_name: String,
    val status: String,
    val last_seen_at: String? = null,
    val app_version: String? = null,
    val created_at: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    store: DeviceProfileStore,
    apiClient: ApiClient? = null,
    onPairHub: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val isPaired = store.getSessionToken() != null
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }

    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun loadDevices() {
        if (apiClient == null || !isPaired) return
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val req = Request.Builder()
                    .url(apiClient.apiUrl("devices"))
                    .get()
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: "[]"
                    devices = jsonParser.decodeFromString<List<DeviceInfo>>(body)
                } else {
                    errorMsg = "Error: ${resp.code}"
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDevices() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                actions = {
                    IconButton(onClick = { loadDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device cards from server
            items(devices.size) { index ->
                val device = devices[index]
                DeviceCard(
                    device = device,
                    isCurrentDevice = device.device_id == store.getDeviceId(),
                    onCopyId = { clipboardManager.setText(AnnotatedString(device.device_id)) }
                )
            }

            // Fallback if no server data: show local device info
            if (devices.isEmpty() && !isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("This Device (Approver)", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            val localDeviceId = store.getDeviceId()
                            if (localDeviceId != null) {
                                Text(
                                    "ID: ${localDeviceId.take(12)}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (isPaired) "Status: Paired ✓" else "Status: Not paired",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPaired) StatusApproved else StatusFailed
                            )
                        }
                    }
                }
            }

            if (errorMsg != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            errorMsg ?: "",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                }
            }

            // Pair Hub CTA
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onPairHub,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isPaired,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pair a Hub Device")
                }
            }

            // Diagnostics
            item {
                OutlinedButton(
                    onClick = onDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Diagnostics")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, isCurrentDevice: Boolean, onCopyId: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentDevice)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (device.device_type == "APPROVER") "📱" else "⚙️",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            device.display_name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            device.device_type + if (isCurrentDevice) " (this device)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    when (device.status) {
                        "ONLINE" -> "Online ✓"
                        "OFFLINE" -> "Offline"
                        else -> device.status
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (device.status == "ONLINE") StatusApproved else StatusFailed
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ID: ${device.device_id.take(12)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onCopyId, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Create,
                        contentDescription = "Copy ID",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (device.last_seen_at != null) {
                Text(
                    "Last seen: ${device.last_seen_at}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
