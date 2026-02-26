package com.kilu.pocketagent.features.pairing

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kilu.pocketagent.shared.models.QRPayload
import kotlinx.serialization.json.Json

private const val TAG = "HubScanScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScanScreen(onScanSuccess: (QRPayload) -> Unit) {
    val context = LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf("Preparing scanner…") }
    var isReady by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    val jsonParser = Json { ignoreUnknownKeys = true }

    fun launchScan() {
        isScanning = true
        errorMsg = null
        statusMsg = "Opening camera…"
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
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
                        errorMsg = "Invalid QR code format. Expected KiLu pairing QR."
                        statusMsg = "Ready to scan"
                    }
                }
                .addOnFailureListener { e ->
                    isScanning = false
                    Log.e(TAG, "Scan failed", e)
                    errorMsg = "Scan failed: ${e.message}"
                    statusMsg = "Ready to scan"
                }
                .addOnCanceledListener {
                    isScanning = false
                    statusMsg = "Scan cancelled. Tap to retry."
                }
        } catch (e: Exception) {
            isScanning = false
            Log.e(TAG, "Cannot start scanner", e)
            errorMsg = "Cannot start scanner: ${e.message}"
            statusMsg = "Scanner error"
        }
    }

    // Ensure GMS scanner module is installed, then auto-launch scan
    LaunchedEffect(Unit) {
        statusMsg = "Checking scanner module…"
        try {
            val scanner = GmsBarcodeScanning.getClient(context)
            val moduleInstallClient = ModuleInstall.getClient(context)

            // Check if module is already available
            moduleInstallClient.areModulesAvailable(scanner)
                .addOnSuccessListener { response ->
                    if (response.areModulesAvailable()) {
                        Log.d(TAG, "Scanner module already available")
                        isReady = true
                        statusMsg = "Scanner ready"
                        launchScan()
                    } else {
                        // Module not available — request install
                        statusMsg = "Downloading scanner module…"
                        Log.d(TAG, "Requesting scanner module install")
                        val installRequest = ModuleInstallRequest.newBuilder()
                            .addApi(scanner)
                            .build()
                        moduleInstallClient.installModules(installRequest)
                            .addOnSuccessListener {
                                Log.d(TAG, "Scanner module installed successfully")
                                isReady = true
                                statusMsg = "Scanner ready"
                                launchScan()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Module install failed", e)
                                isReady = false
                                errorMsg = "Failed to download scanner module: ${e.message}\n\nPlease update Google Play Services and try again."
                                statusMsg = "Module unavailable"
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Module availability check failed", e)
                    // Try launching anyway — module might be present
                    isReady = true
                    statusMsg = "Scanner ready (unverified)"
                    launchScan()
                }
        } catch (e: Exception) {
            Log.e(TAG, "ModuleInstall setup error", e)
            // Fallback: try launching scan directly
            isReady = true
            statusMsg = "Fallback mode"
            launchScan()
        }
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
                "Point the camera at the QR code displayed on the Approver device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isScanning || !isReady) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                statusMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(16.dp))
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
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { launchScan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(48.dp),
                enabled = isReady && !isScanning
            ) {
                Text(
                    when {
                        !isReady -> "Installing module…"
                        isScanning -> "Scanning…"
                        else -> "Scan QR Code"
                    }
                )
            }
        }
    }
}
