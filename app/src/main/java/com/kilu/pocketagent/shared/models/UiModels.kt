package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable

/**
 * Unifies authority binding data to ensure consistent visibility across screens.
 */
@Serializable
data class ApprovalBindingUiModel(
    val runtimeId: String,
    val runtimeLabel: String,
    val toolchainId: String,
    val normalizedAction: String,
    val expiresAt: String? = null,
    val bindingMismatch: Boolean = false
)

/**
 * Represents a single step in the vertical protocol-aligned timeline.
 */
@Serializable
data class TaskLifecycleStep(
    val label: String,       // Human-readable (e.g., "Execution Finalized")
    val protocolState: String, // Protocol state badge (e.g., "DONE")
    val timestamp: String? = null,
    val actor: String? = null,  // "Approver", "Control Plane", "Hub"
    val isCompleted: Boolean = false,
    val isCurrent: Boolean = false,
    val isError: Boolean = false
)

/**
 * Post-run evidence for MVP, derived from Hub SubmitResult and CP state.
 */
@Serializable
data class ExecutionEvidenceUiModel(
    val outcome: String,
    val runtimeId: String,
    val toolchainId: String,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val exitCode: Int? = null,
    val stdoutHash: String? = null,
    val stderrHash: String? = null,
    val receiptRef: String? = null
)
