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
import com.kilu.pocketagent.core.crypto.ActionApprovalV2
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
import com.kilu.pocketagent.core.ui.components.KiluBindingRow
import com.kilu.pocketagent.core.ui.components.KiluCard
import com.kilu.pocketagent.core.ui.components.KiluDivider
import com.kilu.pocketagent.core.ui.components.KiluPrimaryButton
import com.kilu.pocketagent.core.ui.components.KiluSectionHeader
import com.kilu.pocketagent.core.ui.theme.StatusApproved
import androidx.compose.ui.Alignment

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

    // Fail closed for any legacy/missing/changed action binding in this
    // opt-in CREG-003 client. Existing released APK v0.9.39 is untouched.
    val actionBindingError = planData?.let { p ->
        try {
            require(p.approval_protocol == ActionApprovalV2.PROTOCOL) {
                "Plan does not carry KILU_APPROVAL_V2; regenerate with a paired Hub."
            }
            require(!p.avo_hash.isNullOrBlank() && !p.runtime_id.isNullOrBlank()
                    && !p.toolchain_id.isNullOrBlank()) {
                "Plan has incomplete AVO / runtime / toolchain binding."
            }
            ActionApprovalV2.requireActionBinding(p.action_payload_json, p.action_digest)
            ActionApprovalV2.message(p.plan_id,p.avo_hash,p.action_digest!!,p.runtime_id,p.toolchain_id)
            null
        } catch (err: Exception) { err.message ?: "Unverifiable action binding" }
    }


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
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.padding(16.dp)) {
                Text("Approve Execution", style = MaterialTheme.typography.headlineMedium)
                Text("Review authority binding and limits", style = MaterialTheme.typography.bodySmall)
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onBack, enabled = !isApproving, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    if (planData != null && errorMsg == null && actionBindingError == null) {
                            KiluPrimaryButton(
                                text = if (isApproving) "Signing…" else "Confirm",
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

                                            // Revalidate the very same fully displayed bytes
                                            // after biometric approval and before signing.
                                            val visiblePlan = planData ?: throw IllegalStateException("Plan disappeared")
                                            ActionApprovalV2.requireActionBinding(visiblePlan.action_payload_json,visiblePlan.action_digest)
                                            val message = ActionApprovalV2.message(
                                                visiblePlan.plan_id,
                                                requireNotNull(visiblePlan.avo_hash),
                                                requireNotNull(visiblePlan.action_digest),
                                                requireNotNull(visiblePlan.runtime_id),
                                                requireNotNull(visiblePlan.toolchain_id)
                                            )
                                            val sig = keyManager.sign(Role.APPROVER, message)
                                            val pub = keyManager.publicKey(Role.APPROVER)
                                            val deviceId = deviceStore.getDeviceId() ?: throw IllegalStateException("Device not paired")

                                            val payload = ApprovePlanReq(
                                                device_id = deviceId,
                                                biometric_present = true,
                                                approval_receipt = ApprovalReceipt("ED25519", pub, sig,
                                                    ActionApprovalV2.PROTOCOL)
                                            )

                                            val controlPlane = ControlPlaneApi(apiClient.client, apiClient.apiUrl(""))
                                            val result = controlPlane.approvePlan(planData!!.plan_id, payload)
                                            if (result.isSuccess) {
                                                onApproved()
                                            } else {
                                                errorMsg = "Control Plane rejected approval.\n${result.exceptionOrNull()?.message ?: "Unknown error"}"
                                            }
                                        } catch (e: Exception) {
                                            errorMsg = "Approval failed: ${e.message}"
                                        } finally {
                                            isApproving = false
                                        }
                                    }
                                },
                                enabled = !isApproving,
                                loading = isApproving,
                                loadingText = "Signing…",
                                modifier = Modifier.weight(1f)
                            )
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
                if (actionBindingError != null) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Approval blocked — unverifiable exact action",
                                style = MaterialTheme.typography.titleMedium)
                            Text(actionBindingError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // Exact bytes are displayed in full, untruncated, in this
                // vertically scrollable approval view. Their SHA-256 is checked
                // locally before the Confirm button is enabled.
                KiluSectionHeader(label = "Exact execution payload — review all bytes")
                KiluCard {
                    Text(planData!!.action_payload_json ?: "Missing exact payload",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        softWrap = true)
                    KiluDivider()
                    KiluBindingRow("Action SHA-256", planData!!.action_digest ?: "—")
                    KiluBindingRow("AVO SHA-256", planData!!.avo_hash ?: "—")
                    KiluBindingRow("Approval protocol", planData!!.approval_protocol ?: "Legacy / not valid")
                }

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

                // 1. Authority Binding
                KiluSectionHeader(label = "Authority Binding", accentColor = MaterialTheme.colorScheme.primary)
                val isBound = planData!!.runtime_id != null && planData!!.toolchain_id != null
                KiluCard(accentColor = if (isBound) StatusApproved else MaterialTheme.colorScheme.error) {
                    KiluBindingRow("Hub", planData!!.hub_name ?: "Unbound")
                    KiluBindingRow("Runtime ID", planData!!.runtime_id?.take(14) ?: "—")
                    KiluBindingRow("Toolchain", planData!!.toolchain_id ?: "—")
                    KiluDivider()
                    KiluBindingRow(
                        "Binding Integrity",
                        if (isBound) "VERIFIED" else "UNBOUND",
                        valueColor = if (isBound) StatusApproved else MaterialTheme.colorScheme.error
                    )
                }

                // 2. Normalized Action
                planData!!.summary?.let { summary ->
                    KiluSectionHeader(label = "Normalized Action")
                    KiluCard {
                        Text(summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // 3. Resource Budget
                KiluSectionHeader(label = "Resource Budget")
                KiluCard {
                    KiluBindingRow("Max Steps", "${planData!!.max_steps}")
                    KiluBindingRow("Allowlist", planData!!.allowlist_domains.joinToString(", ").ifBlank { "any" })
                    KiluBindingRow("Expires", planData!!.expires_at ?: "—")
                }

                // 4. Execution Sequence
                planData!!.steps_preview?.let { steps ->
                    KiluSectionHeader(label = "Execution Sequence")
                    KiluCard {
                        steps.forEachIndexed { idx, step ->
                            if (idx > 0) KiluDivider()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(24.dp)
                                )
                                Column {
                                    Text(step.op, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(step.desc, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            } else if (errorMsg == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
