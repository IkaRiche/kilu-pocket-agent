package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable

/**
 * WorkflowGrant — parent-level bounded authority for a complete multi-step workflow.
 *
 * Issued once by CP after Approver taps APPROVE ALL.
 * Sealed: step list, task_ids, runtime, toolchain are immutable after issuance.
 * Tamper detection: CP recomputes workflow_ref independently; mismatch = hard abort.
 *
 * E3.2 Phase B — B7 Android Approver one-tap UI.
 */
@Serializable
data class WorkflowGrant(
    val grant_id: String,
    val status: String,                    // active | consumed | expired | revoked
    val workflow_ref: String,              // sha256:... of sealed step list + binding context
    val task_ids: List<String>,
    val target_runtime_id: String,
    val toolchain_id: String,
    val expires_at: String,               // ISO8601
    val consumed_steps: Int = 0,
    val total_steps: Int,
    val created_at: String? = null,
)

/**
 * WorkflowGrantStep — one sealed step within the grant for display.
 * Sent by CP as part of the grant detail response.
 */
@Serializable
data class WorkflowGrantStep(
    val step_index: Int,
    val step_name: String,
    val instruction: String,
    val action_kind: String,
    val task_id: String? = null,
)

/**
 * WorkflowGrantDetail — full grant detail for display on APPROVE ALL screen.
 * Maps GET /v1/workflow-grants/:id response.
 */
@Serializable
data class WorkflowGrantDetail(
    val grant_id: String,
    val status: String,
    val workflow_ref: String,
    val task_ids: List<String>,
    val target_runtime_id: String,
    val toolchain_id: String,
    val expires_at: String,
    val consumed_steps: Int = 0,
    val total_steps: Int,
    val created_at: String? = null,
    val steps: List<WorkflowGrantStep>? = null, // may be null if CP omits it
)

/** Wrapper for GET /v1/workflow-grants/:id response envelope. */
@Serializable
data class WorkflowGrantDetailResp(
    val grant: WorkflowGrantDetail? = null,
    // CP may return flat (no envelope) — handled in API layer
    val grant_id: String? = null,
    val status: String? = null,
    val workflow_ref: String? = null,
    val task_ids: List<String>? = null,
    val target_runtime_id: String? = null,
    val toolchain_id: String? = null,
    val expires_at: String? = null,
    val consumed_steps: Int? = null,
    val total_steps: Int? = null,
    val created_at: String? = null,
    val steps: List<WorkflowGrantStep>? = null,
) {
    /** Unwrap from either envelope or flat response. */
    fun unwrap(): WorkflowGrantDetail? = grant ?: run {
        val id = grant_id ?: return null
        val st = status ?: return null
        val ref = workflow_ref ?: return null
        val ids = task_ids ?: return null
        val rt = target_runtime_id ?: return null
        val tc = toolchain_id ?: return null
        val exp = expires_at ?: return null
        val tot = total_steps ?: return null
        WorkflowGrantDetail(
            grant_id = id, status = st, workflow_ref = ref,
            task_ids = ids, target_runtime_id = rt, toolchain_id = tc,
            expires_at = exp, consumed_steps = consumed_steps ?: 0,
            total_steps = tot, created_at = created_at, steps = steps,
        )
    }
}

/** Approval request body for POST /v1/workflow-grants/:id/approve */
@Serializable
data class ApproveWorkflowGrantReq(
    val device_id: String,
    val biometric_present: Boolean,
    val approval_receipt: ApprovalReceipt,  // reuses existing per-step receipt shape
)

/** Approval response from POST /v1/workflow-grants/:id/approve */
@Serializable
data class ApproveWorkflowGrantResp(
    val grant_id: String,
    val status: String,            // should be "active" after approval
    val tasks_activated: Int? = null,
)

/**
 * Minimal grant list item for the home screen pending grant badge.
 * Populated from GET /v1/workflow-grants?status=active&pending_approval=true.
 * CP may not support this yet — handled gracefully.
 */
@Serializable
data class WorkflowGrantListItem(
    val grant_id: String,
    val status: String,
    val total_steps: Int,
    val target_runtime_id: String,
    val expires_at: String,
    val created_at: String? = null,
)
