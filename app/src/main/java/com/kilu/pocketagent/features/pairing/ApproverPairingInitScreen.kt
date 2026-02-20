package com.kilu.pocketagent.features.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.qr.QrGenerator
import com.kilu.pocketagent.shared.models.InitResponse
import com.kilu.pocketagent.shared.models.QRPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun ApproverPairingInitScreen(apiClient: ApiClient) {
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var initResp by remember { mutableStateOf<InitResponse?>(null) }
    val scope = rememberCoroutineScope()
    
    // Ignore unknown keys to tolerate extra JSON fields
    val jsonParser = Json { ignoreUnknownKeys = true }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pair a Hub", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (qrBitmap != null && initResp != null) {
            Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(250.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Short Code: ${initResp?.short_code ?: "N/A"}")
            val hash = initResp?.offer_core_hash ?: ""
            Text("Hash: ${hash.take(6)}...${hash.takeLast(6)}")
        } else if (errorMsg != null) {
            Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Fetching pairing offer from Control Plane...")
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val req = Request.Builder()
                    .url("${apiClient.getBaseUrl()}/devices/approver/init")
                    .post("{}".toRequestBody(null))
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    val data = jsonParser.decodeFromString<InitResponse>(bodyStr)
                    initResp = data
                    
                    val payload = QRPayload(
                        cp = apiClient.getBaseUrl(),
                        t = data.pairing_token,
                        h = data.offer_core_hash,
                        e = data.expires_at
                    )
                    val jsonStr = jsonParser.encodeToString(payload)
                    qrBitmap = QrGenerator.generate(jsonStr)
                } else {
                    errorMsg = "Server error ${resp.code}: ${resp.body?.string()}"
                }
            } catch (e: Exception) {
                errorMsg = "Cannot reach control plane: ${e.message}"
            }
        }
    }
}
