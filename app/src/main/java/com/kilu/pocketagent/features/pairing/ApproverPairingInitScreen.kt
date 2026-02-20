package com.kilu.pocketagent.features.pairing

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.core.crypto.HashingUtil
import com.kilu.pocketagent.core.crypto.KeyManager
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.qr.QrGenerator
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role
import com.kilu.pocketagent.shared.models.ApproverConfirmReq
import com.kilu.pocketagent.shared.models.ApproverConfirmResp
import com.kilu.pocketagent.shared.models.PairingInitResponse
import com.kilu.pocketagent.shared.models.QRPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun ApproverPairingInitScreen(apiClient: ApiClient, store: DeviceProfileStore, onPaired: () -> Unit) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var initResp by remember { mutableStateOf<PairingInitResponse?>(null) }
    var isConfirming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val jsonParser = Json { ignoreUnknownKeys = true }
    val keyManager = remember { KeyManager(context) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pair a Hub", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (qrBitmap != null && initResp != null) {
            Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(250.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Short Code: ${initResp?.short_code ?: "N/A"}")
            val hash = initResp?.offer_core_hash ?: ""
            Text("Hash: ${hash.take(6)}...${hash.takeLast(6)}")
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    isConfirming = true
                    scope.launch {
                        try {
                            val hashBytes = HashingUtil.extractHashBytesToSign(initResp!!.offer_core_hash)
                            keyManager.ensureKey(Role.APPROVER)
                            val signatureB64 = keyManager.sign(Role.APPROVER, hashBytes)
                            val pubkeyB64 = keyManager.publicKey(Role.APPROVER)
                            
                            val reqPayload = ApproverConfirmReq(
                                pairing_token = initResp!!.pairing_token,
                                display_name = "Approver Device ${Build.MODEL}",
                                pubkey_b64 = pubkeyB64,
                                signature_b64 = signatureB64
                            )
                            val bodyBytes = jsonParser.encodeToString(reqPayload).toByteArray()
                            val request = Request.Builder()
                                .url("${apiClient.getBaseUrl()}/v1/devices/approver/confirm")
                                .post(bodyBytes.toRequestBody("application/json".toMediaType()))
                                .build()
                            
                            val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(request).execute() }
                            if (resp.isSuccessful) {
                                val bodyStr = resp.body?.string() ?: ""
                                val data = jsonParser.decodeFromString<ApproverConfirmResp>(bodyStr)
                                store.setDeviceId(data.device_id)
                                store.setTenantId(data.tenant_id)
                                store.setSessionToken(data.device_session_token)
                                onPaired()
                            } else {
                                errorMsg = "Confirm failed: ${resp.code} ${resp.body?.string()}"
                            }
                        } catch (e: Exception) {
                            errorMsg = "Confirm caught: ${e.message}"
                        }
                        isConfirming = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConfirming
            ) {
                Text(if (isConfirming) "Confirming..." else "Confirm this Approver")
            }
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
                    .url("${apiClient.getBaseUrl()}/v1/devices/approver/init")
                    .post("{}".toRequestBody(null))
                    .build()
                val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    val data = jsonParser.decodeFromString<PairingInitResponse>(bodyStr)
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
