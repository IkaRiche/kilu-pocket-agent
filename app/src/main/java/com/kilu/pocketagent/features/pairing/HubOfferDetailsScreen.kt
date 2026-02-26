package com.kilu.pocketagent.features.pairing

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kilu.pocketagent.BuildConfig
import com.kilu.pocketagent.core.crypto.CryptoUtils
import com.kilu.pocketagent.core.crypto.HashingUtil
import com.kilu.pocketagent.core.crypto.KeyManager
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role
import com.kilu.pocketagent.shared.models.HubConfirmReq
import com.kilu.pocketagent.shared.models.HubConfirmResp
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
fun HubOfferDetailsScreen(
    payload: QRPayload, 
    apiClient: ApiClient, 
    store: DeviceProfileStore, 
    onPaired: () -> Unit
) {
    val context = LocalContext.current
    val isSigValid = CryptoUtils.verifyServerSig(payload.h, payload.ss, payload.kid)
    var isConfirming by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val keyManager = remember { KeyManager(context) }
    
    // Validations
    val isCpMatching = payload.cp == store.getControlPlaneUrl()
    val isExpired = try { 
        java.time.Instant.parse(payload.e).isBefore(java.time.Instant.now())
    } catch(e: Exception) { false }
    
    val canConfirm = isSigValid && isCpMatching && !isExpired && !isConfirming

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
        
        if (!isSigValid) Text("WARNING: Server signature invalid!", color = MaterialTheme.colorScheme.error)
        if (!isCpMatching) Text("WARNING: QR Control Plane differs from your target URL!", color = MaterialTheme.colorScheme.error)
        if (isExpired) Text("WARNING: Pairing token is expired.", color = MaterialTheme.colorScheme.error)
        if (errorMsg != null) Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)

        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                isConfirming = true
                scope.launch {
                    try {
                        val hashBytes = HashingUtil.extractHashBytesToSign(payload.h)
                        keyManager.ensureKey(Role.HUB)
                        val signatureB64 = keyManager.sign(Role.HUB, hashBytes)
                        val pubkeyB64 = keyManager.publicKey(Role.HUB)
                        
                        val reqPayload = HubConfirmReq(
                            hub_link_code = payload.t,
                            display_name = "Hub Device ${Build.MODEL}",
                            pubkey_b64 = pubkeyB64,
                            signature_b64 = signatureB64
                        )
                        val jsonStr = jsonParser.encodeToString(reqPayload)
                        val request = Request.Builder()
                            .url(apiClient.apiUrl("hubs/confirm"))
                            .post(jsonStr.toByteArray().toRequestBody("application/json".toMediaType()))
                            .build()
                        
                        val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(request).execute() }
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            val data = jsonParser.decodeFromString<HubConfirmResp>(bodyStr)
                            store.setDeviceId(data.device_id)
                            store.setTenantId(data.tenant_id)
                            store.setSessionToken(data.hub_session_token)
                            onPaired()
                        } else {
                            errorMsg = com.kilu.pocketagent.shared.utils.ErrorHandler.parseError(resp)
                        }
                    } catch (e: Exception) {
                        errorMsg = "Confirm caught: ${e.message}"
                    }
                    isConfirming = false
                }
            },
            enabled = canConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isConfirming) "Confirming..." else "Confirm & Connect")
        }
    }
}
