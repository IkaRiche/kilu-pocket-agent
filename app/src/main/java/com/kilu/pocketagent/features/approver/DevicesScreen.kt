package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.ui.theme.StatusApproved
import com.kilu.pocketagent.core.ui.theme.StatusFailed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    store: DeviceProfileStore,
    onPairHub: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val tenantId = store.getTenantId()
    val deviceId = store.getDeviceId()
    val isPaired = tenantId != null && deviceId != null
    var showDetails by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── Approver Device Status Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Approver Device", style = MaterialTheme.typography.titleMedium)
                        val statusColor = if (isPaired) StatusApproved else StatusFailed
                        val statusText = if (isPaired) "Paired ✓" else "Not paired"
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isPaired) {
                        // Collapsed by default — short prefix + copy
                        DetailRow(
                            label = "Tenant",
                            value = tenantId?.take(12) ?: "",
                            fullValue = tenantId ?: "",
                            onCopy = { clipboardManager.setText(AnnotatedString(tenantId ?: "")) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        DetailRow(
                            label = "Device",
                            value = deviceId?.take(12) ?: "",
                            fullValue = deviceId ?: "",
                            onCopy = { clipboardManager.setText(AnnotatedString(deviceId ?: "")) }
                        )

                        if (showDetails) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Control Plane: ${store.getControlPlaneUrl() ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Role: ${store.getRole()?.name ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide details" else "Show details")
                        }
                    } else {
                        Text(
                            "Device not registered. Complete Approver pairing first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Pair Hub CTA ──
            Button(
                onClick = onPairHub,
                modifier = Modifier.fillMaxWidth(),
                enabled = isPaired,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pair a Hub Device")
            }

            if (!isPaired) {
                Text(
                    "Complete Approver registration first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Diagnostics ──
            OutlinedButton(
                onClick = onDiagnostics,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("View Diagnostics")
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    fullValue: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label: ${value}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "Copy $label",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
