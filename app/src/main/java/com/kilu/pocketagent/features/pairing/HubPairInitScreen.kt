package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.BuildConfig
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.shared.models.PairingInitResponse
import com.kilu.pocketagent.shared.models.QRPayload
import com.kilu.pocketagent.core.qr.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun HubPairInitScreen(
    apiClient: ApiClient,
    store: DeviceProfileStore,
    onBack: () -> Unit
) {
    var initResp by remember { mutableStateOf<PairingInitResponse?>(null) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pair a Hub Device", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Show this QR code to the Hub device to complete pairing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMsg != null) {
            Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (qrBitmap != null && initResp != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "Hub Pairing QR Code",
                        modifier = Modifier.size(250.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val hash = initResp?.getEffectiveHash() ?: ""
                    Text(
                        "Offer Hash: ${hash.take(10)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Expires: ${initResp?.getEffectiveExpiresAt() ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Control Plane: ${apiClient.baseOrigin()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (errorMsg == null) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Generating Hub pairing offer…")
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val body = jsonParser.encodeToString(
                    mapOf("approver_device_id" to (store.getDeviceId() ?: ""))
                )
                val req = Request.Builder()
                    .url(apiClient.apiUrl("hubs/init"))
                    .post(body.toByteArray().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                val bodyStr = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val data = jsonParser.decodeFromString<PairingInitResponse>(bodyStr)
                    initResp = data

                    val payload = QRPayload(
                        cp = apiClient.baseOrigin(),
                        t = data.getEffectiveToken(),
                        h = data.getEffectiveHash(),
                        e = data.getEffectiveExpiresAt(),
                        ss = data.server_sig ?: data.qr_payload?.server_sig?.sig_b64,
                        kid = BuildConfig.SERVER_KID
                    )
                    val jsonStr = jsonParser.encodeToString(payload)
                    qrBitmap = QrGenerator.generate(jsonStr)
                } else {
                    errorMsg = "Hub init failed (${resp.code}): $bodyStr"
                }
            } catch (e: Exception) {
                errorMsg = "Cannot reach control plane: ${e.message}"
            }
        }
    }
}
