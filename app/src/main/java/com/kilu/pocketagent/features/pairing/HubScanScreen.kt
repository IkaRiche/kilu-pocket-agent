package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kilu.pocketagent.shared.models.QRPayload
import kotlinx.serialization.json.Json

@Composable
fun HubScanScreen(onScanSuccess: (QRPayload) -> Unit) {
    val context = LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val jsonParser = Json { ignoreUnknownKeys = true }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scan Approver QR", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.weight(1f))
        
        if (errorMsg != null) {
            Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Button(
            onClick = {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .enableAutoZoom()
                    .build()
                val scanner = GmsBarcodeScanning.getClient(context, options)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue ?: ""
                        try {
                            val payload = jsonParser.decodeFromString<QRPayload>(raw)
                            onScanSuccess(payload)
                        } catch (e: Exception) {
                            errorMsg = "Invalid QR Format: $raw"
                        }
                    }
                    .addOnFailureListener { e ->
                        errorMsg = "Scan failed: ${e.message}"
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Camera Scanner")
        }
    }
}
