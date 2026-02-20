package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kilu.pocketagent.core.crypto.BiometricGate
import com.kilu.pocketagent.core.crypto.KeyManager
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.Role
import com.kilu.pocketagent.shared.models.ApprovePlanReq
import com.kilu.pocketagent.shared.models.PlanPreviewResp
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun PlanPreviewScreen(
    taskId: String,
    apiClient: ApiClient,
    onApproved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var planData by remember { mutableStateOf<PlanPreviewResp?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isApproving by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true }
    val keyManager = remember { KeyManager(context) }

    LaunchedEffect(taskId) {
        try {
            val req = Request.Builder()
                .url("\${apiClient.getBaseUrl()}/v1/tasks/\$taskId/plan")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            val resp = withContext(Dispatchers.IO) { apiClient.client.newCall(req).execute() }
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                planData = jsonParser.decodeFromString<PlanPreviewResp>(bodyStr)
            } else {
                errorMsg = ErrorHandler.parseError(resp)
            }
        } catch (e: Exception) {
            errorMsg = "Failed to fetch plan: \${e.message}"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Execution Plan", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (planData != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Plan ID: \${planData!!.plan_id.take(8)}...")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Allowed Domains: \${planData!!.allowlist_domains.joinToString()}")
                    Text("Max Steps: \${planData!!.max_steps}")
                    Text("Expires At: \${planData!!.expires_at}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Forbidden Flags: \${planData!!.forbidden_flags}")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("One approval enables autonomous execution within limits (domain, steps, time).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            
        } else if (errorMsg != null) {
            Text("Error: \$errorMsg", color = MaterialTheme.colorScheme.error)
        } else {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Analyzing bounds...")
        }

        Spacer(modifier = Modifier.weight(1f))
        
        if (errorMsg != null) {
            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Status: \$errorMsg", color = MaterialTheme.colorScheme.error)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, enabled = !isApproving) {
                Text("Back")
            }
            if (planData != null) {
                Button(
                    onClick = {
                        isApproving = true
                        errorMsg = null
                        scope.launch {
                            try {
                                val activity = context as? FragmentActivity
                                if (activity == null) {
                                    errorMsg = "Host is not a FragmentActivity, cannot prompt biometrics."
                                    return@launch
                                }
                                
                                val authSuccess = BiometricGate.confirm(
                                    activity,
                                    "Approve Plan",
                                    "Authorize Hub to execute \${planData!!.max_steps} steps autonomously."
                                )
                                
                                if (!authSuccess) {
                                    errorMsg = "Biometric Auth Cancelled/Failed."
                                    return@launch
                                }
                                
                                // Sign the "approval_receipt"
                                val messageBytes = ("receipt:" + planData!!.plan_id).toByteArray() // Strict signature schema requirement
                                val signatureB64 = keyManager.sign(Role.APPROVER, messageBytes)
                                val pubkeyB64 = keyManager.publicKey(Role.APPROVER)
                                
                                val approvePayload = ApprovePlanReq(
                                    pubkey_b64 = pubkeyB64,
                                    signature_b64 = signatureB64
                                )
                                
                                val appReq = Request.Builder()
                                    .url("\${apiClient.getBaseUrl()}/v1/plans/\${planData!!.plan_id}/approve")
                                    .post(jsonParser.encodeToString(approvePayload).toByteArray().toRequestBody("application/json".toMediaType()))
                                    .build()
                                
                                val appResp = withContext(Dispatchers.IO) { apiClient.client.newCall(appReq).execute() }
                                
                                if (appResp.isSuccessful) {
                                    onApproved()
                                } else {
                                    errorMsg = ErrorHandler.parseError(appResp)
                                }
                                
                            } catch (e: Exception) {
                                errorMsg = "Crash: \${e.message}"
                            } finally {
                                isApproving = false
                            }
                        }
                    },
                    enabled = !isApproving
                ) {
                    Text(if (isApproving) "Authorizing..." else "Approve Plan")
                }
            }
        }
    }
}
