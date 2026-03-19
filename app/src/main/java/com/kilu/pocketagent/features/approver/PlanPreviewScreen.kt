package com.kilu.pocketagent.features.approver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kilu.pocketagent.core.crypto.BiometricGate
import com.kilu.pocketagent.core.crypto.KeyManager
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role
import com.kilu.pocketagent.shared.models.*
import com.kilu.pocketagent.shared.utils.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.kilu.pocketagent.core.network.ControlPlaneApi

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
    var bindingWarning by remember { mutableStateOf<String?>(null) }
    var isApproving by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val keyManager = remember { KeyManager(context) }
    val deviceStore = remember { DeviceProfileStore(context) }
    val scrollState = rememberScrollState()

    LaunchedEffect(taskId) {
        try {
            val controlPlane = ControlPlaneApi(apiClient.client, apiClient.apiUrl(""))
            val plan = controlPlane.getPlan(taskId)
            if (plan != null) {
                // Note: runtime_id/toolchain_id may be null if no Hub is paired yet.
                // This is a UX warning only — real authority binding is enforced
                // server-side during mint-step-batch (ERR_GRANT_NOT_ACTIVE etc).
                if (plan.runtime_id == null || plan.toolchain_id == null) {
                    bindingWarning = "Hub runtime not yet bound. Approval will be recorded but execution requires a paired Hub."
                }
                planData = plan
            } else {
                errorMsg = "Failed to fetch plan."
            }
        } catch (e: Exception) {
            errorMsg = "Fetch error: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.padding(16.dp)) {
                Text("Approve Execution", style = MaterialTheme.typography.headlineMedium)
                Text("Review authority binding and limits", style = MaterialTheme.typography.bodySmall)
            }
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onBack, enabled = !isApproving, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    if (planData != null && errorMsg == null) {
                        Button(
                            onClick = {
                                isApproving = true
                                errorMsg = null
                                scope.launch {
                                    try {
                                        val activity = context as? FragmentActivity ?: throw IllegalStateException("Not a FragmentActivity")
                                        val authSuccess = BiometricGate.confirm(
                                            activity,
                                            "Authorize Task",
                                            "Sign authority binding for hub execution."
                                        )
                                        if (!authSuccess) {
                                            errorMsg = "Biometric session cancelled."
                                            return@launch
                                        }

                                        val message = "receipt:${planData!!.plan_id}"
                                        val sig = keyManager.sign(Role.APPROVER, message.toByteArray())
                                        val pub = keyManager.publicKey(Role.APPROVER)
                                        val deviceId = deviceStore.getDeviceId() ?: throw IllegalStateException("Device not paired")

                                        val payload = ApprovePlanReq(
                                            device_id = deviceId,
                                            biometric_present = true,
                                            approval_receipt = ApprovalReceipt("ED25519", pub, sig)
                                        )

                                        val controlPlane = ControlPlaneApi(apiClient.client, apiClient.apiUrl(""))
                                        val resp = controlPlane.approvePlan(planData!!.plan_id, payload)
                                        if (resp != null) onApproved() else errorMsg = "Control Plane rejected approval."
                                    } catch (e: Exception) {
                                        errorMsg = "Approval failed: ${e.message}"
                                    } finally {
                                        isApproving = false
                                    }
                                }
                            },
                            enabled = !isApproving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isApproving) "Signing..." else "Confirm")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(scrollState)) {
            if (errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Blocking Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (planData != null) {
                // Binding warning (not a blocker — real check is server-side)
                if (bindingWarning != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("⚠️ Hub Not Bound", style = MaterialTheme.typography.titleSmall)
                            Text(bindingWarning!!, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // 1. Authority Binding Section
                Text("Authority Binding", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Hub Name", style = MaterialTheme.typography.labelSmall)
                            Text(planData!!.hub_name ?: "Unbound", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Runtime ID", style = MaterialTheme.typography.labelSmall)
                            Text(planData!!.runtime_id?.take(12) ?: "—", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Toolchain", style = MaterialTheme.typography.labelSmall)
                            Text(planData!!.toolchain_id ?: "—", style = MaterialTheme.typography.bodySmall)
                        }
                        Divider(Modifier.padding(vertical = 8.dp))
                        val bindingStatus = if (planData!!.runtime_id != null && planData!!.toolchain_id != null) "VERIFIED" else "UNBOUND"
                        Text("Binding Integrity: $bindingStatus", style = MaterialTheme.typography.labelSmall,
                            color = if (bindingStatus == "VERIFIED") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 2. Normalized Action summary
                Text("Normalized Action", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                planData!!.summary?.let {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 3. Resource Limits
                Text("Resource Budget", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Max Auto Steps: ${planData!!.max_steps}", style = MaterialTheme.typography.bodySmall)
                        Text("Allowlist: ${planData!!.allowlist_domains.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        Text("Expires: ${planData!!.expires_at}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(16.dp))
                planData!!.steps_preview?.let { steps ->
                    Text("Execution Sequence", style = MaterialTheme.typography.titleSmall)
                    steps.forEachIndexed { idx, step ->
                        Text("${idx+1}. ${step.op}: ${step.desc}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else if (errorMsg == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
