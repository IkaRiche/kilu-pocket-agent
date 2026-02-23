package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kilu.pocketagent.shared.models.QRPayload
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScanScreen(onScanSuccess: (QRPayload) -> Unit) {
    val context = LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    val jsonParser = Json { ignoreUnknownKeys = true }

    fun startScan() {
        isScanning = true
        errorMsg = null
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    isScanning = false
                    val raw = barcode.rawValue ?: ""
                    try {
                        val payload = jsonParser.decodeFromString<QRPayload>(raw)
                        onScanSuccess(payload)
                    } catch (e: Exception) {
                        errorMsg = "Invalid QR Format. Expected KiLu pairing code."
                    }
                }
                .addOnFailureListener { e ->
                    isScanning = false
                    errorMsg = "Scanner failed: ${e.message}\n\nMake sure Google Play Services is up to date and camera permission is granted."
                }
                .addOnCanceledListener {
                    isScanning = false
                    errorMsg = "Scan cancelled. Tap 'Scan QR Code' to try again."
                }
        } catch (e: Exception) {
            isScanning = false
            errorMsg = "Cannot start scanner: ${e.message}\n\nEnsure Google Play Services is installed."
        }
    }

    // Auto-start scan on first compose
    LaunchedEffect(Unit) {
        startScan()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("Scan Approver QR", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Point the camera at the QR code shown on the Approver device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isScanning) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Opening camera…", style = MaterialTheme.typography.bodyMedium)
            }

            if (errorMsg != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMsg ?: "",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { startScan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(48.dp),
                enabled = !isScanning
            ) {
                Text(if (isScanning) "Scanning…" else "Scan QR Code")
            }
        }
    }
}
