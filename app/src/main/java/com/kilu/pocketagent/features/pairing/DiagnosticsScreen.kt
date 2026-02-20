package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.crypto.KeyManager
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import java.security.MessageDigest

@Composable
fun DiagnosticsScreen(store: DeviceProfileStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val keyManager = remember { KeyManager(context) }
    
    val role = store.getRole()
    val fingerprint = try {
        if (role != null) {
            val pubB64 = keyManager.publicKey(role)
            val bytes = android.util.Base64.decode(pubB64, android.util.Base64.NO_WRAP)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.take(8).joinToString("") { "%02x".format(it) }
        } else {
            "No Role"
        }
    } catch(e: Exception) { "Error generating: ${e.message}" }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Role: ${role?.name ?: "None"}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Control Plane: ${store.getControlPlaneUrl()}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                val tId = store.getTenantId()
                Text("Tenant ID: ${if (tId != null) tId.take(8) + "..." else "Not Set"}")
                
                val dId = store.getDeviceId()
                Text("Device ID: ${if (dId != null) dId.take(8) + "..." else "Not Set"}")
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pubkey Fingerprint (First 8 Bytes):")
                Text(fingerprint, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
