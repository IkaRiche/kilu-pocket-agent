package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@Composable
fun PairingHomeScreen(
    store: DeviceProfileStore, 
    apiClient: ApiClient,
    onChangeRole: () -> Unit,
    onPairInit: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val role = store.getRole()
    val isPaired = store.getSessionToken() != null
    var inboxStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Dashboard (${role?.name})", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connection Status: ${if (isPaired) "Paired \u2705" else "Not Paired \u274C"}", style = MaterialTheme.typography.titleMedium)
                if (isPaired) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tenant: ${store.getTenantId()?.take(8)}...")
                    Text("Device ID: ${store.getDeviceId()?.take(8)}...")
                    
                    if (inboxStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Inbox Check: $inboxStatus", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (isPaired) {
            Button(
                onClick = {
                    scope.launch {
                        inboxStatus = "Checking..."
                        try {
                            val request = Request.Builder()
                                .url("${apiClient.getBaseUrl()}/v1/inbox?max=1")
                                .get()
                                .build()
                            val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(request).execute() }
                            if (resp.isSuccessful) {
                                inboxStatus = "OK (200)"
                            } else if (resp.code == 401 || resp.code == 403) {
                                inboxStatus = "Auth Error ${resp.code}. Auto-resetting..."
                                store.clearPairing()
                            } else {
                                inboxStatus = "Error: ${resp.code}"
                            }
                        } catch (e: Exception) {
                            inboxStatus = "Network Error"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sanity Check (/inbox)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { store.clearPairing() }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset Pairing")
            }
        } else {
            Button(onClick = onPairInit, modifier = Modifier.fillMaxWidth()) {
                val actionText = if (role == Role.APPROVER) "Create Pairing QR" else "Scan Approver QR"
                Text(actionText)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onChangeRole, modifier = Modifier.fillMaxWidth()) {
            Text("Switch Role (Wipes Keys!)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Diagnostics (Dev)")
        }
    }
}
