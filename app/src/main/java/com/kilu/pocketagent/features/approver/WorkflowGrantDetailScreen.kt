package com.kilu.pocketagent.features.approver

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.kilu.pocketagent.core.crypto.BiometricGate
import com.kilu.pocketagent.core.crypto.KeyManager
import com.kilu.pocketagent.core.network.ApiClient
import com.kilu.pocketagent.core.network.ControlPlaneApi
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role
import com.kilu.pocketagent.core.ui.theme.StatusApproved
import com.kilu.pocketagent.core.ui.theme.StatusPending
import com.kilu.pocketagent.shared.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WorkflowGrantDetailScreen — B7 one-tap APPROVE ALL screen.
 *
 * Displayed when user navigates to approver_workflow_grant/{grantId}.
 * Shows: WORKFLOW GRANT REQUEST badge, Grant ID, workflow_ref (truncated),
 * runtime binding, toolchain, TTL, step count, sealed step list,
 * [APPROVE ALL] (biometric-gated) and [DENY] buttons.
 *
 * Biometric gate: same Ed25519 signing path as per-step PlanPreviewScreen.
 * After approval: CP activates all PLANNING tasks atomically to READY_FOR_EXECUTION.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowGrantDetailScreen(
    grantId: String,
    apiClient: ApiClient,
    store: DeviceProfileStore,
    onApproved: () -> Unit,
    onDenied: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val controlPlane = remember {
        ControlPlaneApi(apiClient.client, apiClient.apiUrl("")) { onBack() }
    }

    var grant by remember { mutableStateOf<WorkflowGrantDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isApproving by remember { mutableStateOf(false) }
    var isDenying by remember { mutableStateOf(false) }
    var approveError by remember { mutableStateOf<String?>(null) }
    var showDenyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(grantId) {
        isLoading = true
        errorMsg = null
        val g = controlPlane.getWorkflowGrant(grantId)
        if (g != null) grant = g
        else errorMsg = "Failed to load workflow grant. It may have expired or been revoked."
        isLoading = false
    }

    if (showDenyConfirm) {
        AlertDialog(
            onDismissRequest = { showDenyConfirm = false },
            icon = { Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Deny Workflow?") },
            text = { Text("All ${grant?.total_steps ?: "?"} steps will be cancelled. The agent will not proceed.") },
            confirmButton = {
                Button(
                    onClick = { showDenyConfirm = false; onDenied() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Deny") }
            },
            dismissButton = { TextButton(onClick = { showDenyConfirm = false }) { Text("Keep") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Workflow Grant", style = MaterialTheme.typography.titleMedium)
                        Text(grantId.take(12) + "…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading workflow grant…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                errorMsg != null -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMsg ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(onClick = onBack) { Text("Back") }
                    }
                }
                grant != null -> {
                    val g = grant!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { GrantHeaderBadge(grant = g) }
                        item { GrantInfoCard(grant = g) }
                        item {
                            Text(
                                "Sealed Step List (${g.steps?.size ?: g.total_steps} steps)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        val steps = g.steps
                        if (!steps.isNullOrEmpty()) {
                            itemsIndexed(steps) { _, step -> GrantStepRow(step = step, total = g.total_steps) }
                        } else {
                            itemsIndexed(g.task_ids) { idx, taskId -> GrantTaskIdRow(index = idx, taskId = taskId) }
                        }
                        item { SecurityInvariantNote() }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (approveError != null) {
                                Text(approveError!!, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { showDenyConfirm = true },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isApproving && !isDenying,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Deny")
                                }
                                Button(
                                    onClick = {
                                        val capturedContext = context
                                        scope.launch {
                                            isApproving = true
                                            approveError = null
                                            performWorkflowGrantApproval(
                                                context = capturedContext, controlPlane = controlPlane,
                                                store = store, grantId = grantId,
                                                onSuccess = { onApproved() },
                                                onError = { msg -> approveError = msg; isApproving = false }
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(2f),
                                    enabled = !isApproving && !isDenying && g.status == "active",
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusApproved)
                                ) {
                                    if (isApproving) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("APPROVE ALL", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            if (g.status != "active") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Grant is ${g.status} — cannot approve",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}

// ── Composable helpers ───────────────────────────────────────────────

@Composable
private fun GrantHeaderBadge(grant: WorkflowGrantDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column {
                Text("WORKFLOW GRANT REQUEST", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${grant.total_steps} steps · 1 tap to approve all",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun GrantInfoCard(grant: WorkflowGrantDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Status", grant.status.uppercase())
            InfoRow("Runtime", grant.target_runtime_id.take(14) + "…")
            InfoRow("Toolchain", grant.toolchain_id)
            InfoRow("Steps", "${grant.consumed_steps}/${grant.total_steps}")
            InfoRow("Expires", formatExpiry(grant.expires_at))
            InfoRow(label = "Seal (workflow_ref)", value = grant.workflow_ref.take(20) + "…", isHash = true)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isHash: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value,
            style = if (isHash) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GrantStepRow(step: WorkflowGrantStep, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("${step.step_index + 1}", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(step.step_name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionKindBadge(step.action_kind)
                    step.task_id?.let {
                        Text(it.take(8) + "…", style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    step.instruction.take(80) + if (step.instruction.length > 80) "…" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GrantTaskIdRow(index: Int, taskId: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(taskId, style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ActionKindBadge(kind: String) {
    val bg = when (kind) { "shell" -> MaterialTheme.colorScheme.tertiaryContainer; "goose" -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
    val fg = when (kind) { "shell" -> MaterialTheme.colorScheme.onTertiaryContainer; "goose" -> MaterialTheme.colorScheme.onSecondaryContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Box(modifier = Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(kind, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SecurityInvariantNote() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🔒 Seal invariants", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
            Text(
                "• Step list sealed before requesting approval — cannot expand after.\n" +
                "• CP recomputes workflow_ref independently (tamper detection).\n" +
                "• Grant bound to single runtime — no mid-workflow migration.\n" +
                "• Tap APPROVE ALL once. Steps execute without additional taps.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp
            )
        }
    }
}

// ── Business logic ───────────────────────────────────────────────

/**
 * Biometric-gated workflow grant approval.
 * Mirrors PlanPreviewScreen: BiometricGate.confirm -> KeyManager.sign -> POST .../approve.
 */
private suspend fun performWorkflowGrantApproval(
    context: Context,
    controlPlane: ControlPlaneApi,
    store: DeviceProfileStore,
    grantId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val activity = context as? FragmentActivity
            ?: throw IllegalStateException("Context is not a FragmentActivity")
        val deviceId = store.getDeviceId() ?: throw IllegalStateException("Device not paired")

        val authSuccess = BiometricGate.confirm(
            activity = activity,
            title = "Authorize Workflow",
            subtitle = "Sign authority binding for grant ${grantId.take(12)}"
        )
        if (!authSuccess) {
            withContext(Dispatchers.Main) { onError("Biometric authentication cancelled") }
            return
        }

        val keyManager = KeyManager(context)
        val message = "receipt:$grantId"
        val sig = keyManager.sign(Role.APPROVER, message.toByteArray())
        val pub = keyManager.publicKey(Role.APPROVER)

        val req = ApproveWorkflowGrantReq(
            device_id = deviceId,
            biometric_present = true,
            approval_receipt = ApprovalReceipt(pubkey_alg = "ED25519", pubkey_b64 = pub, signature_b64 = sig)
        )

        val result = controlPlane.approveWorkflowGrant(grantId, req)
        withContext(Dispatchers.Main) {
            if (result.isSuccess) onSuccess()
            else onError(result.exceptionOrNull()?.message ?: "Approval failed")
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onError("Approval failed: ${e.message}") }
    }
}

// ── Util ──────────────────────────────────────────────────────────────────────────

private fun formatExpiry(expiresAt: String): String = try {
    val instant = java.time.Instant.parse(expiresAt)
    val remaining = java.time.Duration.between(java.time.Instant.now(), instant)
    val mins = remaining.toMinutes()
    when {
        mins < 0 -> "EXPIRED"
        mins < 60 -> "in ${mins}min"
        else -> "in ${remaining.toHours()}h ${mins % 60}min"
    }
} catch (_: Exception) { expiresAt.take(16) }
