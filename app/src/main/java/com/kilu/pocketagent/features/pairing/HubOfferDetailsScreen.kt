package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.crypto.CryptoUtils
import com.kilu.pocketagent.shared.models.QRPayload

@Composable
fun HubOfferDetailsScreen(payload: QRPayload, onConfirm: () -> Unit) {
    val isSigValid = CryptoUtils.verifyServerSig(payload.h, null)
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Confirm Pairing", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Control Plane: ${payload.cp}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Offer Hash: ${payload.h.take(8)}...", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Expires: ${payload.e}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        if (!isSigValid) {
            Text("WARNING: Server signature invalid!", color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onConfirm,
            enabled = isSigValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm & Connect (Stub)")
        }
    }
}
