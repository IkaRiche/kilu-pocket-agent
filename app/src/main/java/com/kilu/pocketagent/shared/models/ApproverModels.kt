package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApproverTaskItem(
    val task_id: String,
    val title: String? = null,
    val user_prompt: String? = null,
    val status: String,
    val created_at: String? = null,
    val final_report_status: String? = "PENDING",
    val final_report: String? = null,
    val active_grant_id: String? = null,  // legacy per-step grant (grt_ prefix)
    val workflow_grant_id: String? = null, // E3.2 Phase B: set when task is under a workflow grant (wfg_ prefix)
)

// ── Full task detail — maps GET /v1/tasks/:id exactly ──────────────────────

@Serializable
data class TaskEvidence(
    val task_id: String? = null,
    val step_id: String? = null,
    val runner_id: String? = null,
    val adapter: String? = null,
    val outcome: String? = null,
    val started_at: String? = null,
    val finished_at: String? = null,
    val exit_code: Int? = null,
    val stdout_hash: String? = null,
    val stdout_tail: String? = null,
    val evidence_type: String? = null,
)

@Serializable
data class FinalReport(
    val summary: String? = null,
    val detail: String? = null,
    val evidence: List<TaskEvidence>? = null,
    val resolved_at: String? = null,
    val resolver_id: String? = null,
)

@Serializable
data class ApprovalInfo(
    val approved_by: String? = null,
    val approved_at: String? = null,
    val receipt_hash: String? = null,
)

@Serializable
data class PlanStep(
    val step_id: String? = null,
    val step_index: Int? = null,
    val action_kind: String? = null,
    val instruction: String? = null,
    val status: String? = null,
)

@Serializable
data class TaskDetail(
    val task_id: String,
    val title: String? = null,
    val user_prompt: String? = null,
    val status: String,
    val target_runtime_id: String? = null,
    val plan_id: String? = null,
    val result: String? = null,
    val failure_code: String? = null,
    val failure_message: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val final_report: FinalReport? = null,
    val final_report_status: String? = null,
    val approval: ApprovalInfo? = null,
    val steps: List<PlanStep>? = null,
    val evidence: List<TaskEvidence>? = null,
    val active_grant_id: String? = null,
    val workflow_grant_id: String? = null,
)

@Serializable
data class TaskCreated(
    val task_id: String,
    val status: String,
    val target_runtime_id: String? = null,
    val planner_mode: String? = null,
)

@Serializable
data class QuotasResp(
    val planner_credits: Int,
    val executor_credits: Int,
    val planner_credits_used: Int = 0,
    val executor_credits_used: Int = 0,
)

@Serializable
data class DevicesResp(
    val devices: List<DeviceItem>,
)

@Serializable
data class DeviceItem(
    val device_id: String,
    val device_type: String,
    val display_name: String? = null,
    val status: String? = null,
    val runtime_id: String? = null,
    val paired_at: String? = null,
)

@Serializable
data class PlanPreviewResp(
    val plan_id: String? = null,
    val task_id: String? = null,
    val steps: List<PlanPreviewStep>? = null,
    val runtime_id: String? = null,
    val toolchain_id: String? = null,
    val runtime_label: String? = null,
    val toolchain_label: String? = null,
    val window_grant_id: String? = null,
)

@Serializable
data class PlanPreviewStep(
    val step_id: String? = null,
    val step_index: Int? = null,
    val action_kind: String? = null,
    val instruction: String? = null,
)

@Serializable
data class ApprovePlanReq(
    val device_id: String,
    val biometric_present: Boolean,
    val approval_receipt: ApprovalReceipt,
    val plan_id: String? = null,
)

@Serializable
data class ApprovalReceipt(
    val receipt: String,
    val signed_payload: String? = null,
    val algorithm: String? = null,
    val kid: String? = null,
)

@Serializable
data class ApprovePlanResp(
    val task_id: String? = null,
    val status: String? = null,
    val granted_at: String? = null,
    val plan_id: String? = null,
)

@Serializable
data class WindowGrant(
    val grant_id: String,
    val scope_kind: String,
    val status: String,
    val target_runtime_id: String,
    val toolchain_id: String,
    val expires_at: String,
    val created_at: String? = null,
)

@Serializable
data class AssumptionPacket(
    val packet_id: String,
    val task_id: String,
    val status: String,
    val questions: List<AssumptionQuestion> = emptyList(),
    val created_at: String? = null,
)

@Serializable
data class AssumptionQuestion(
    val question_id: String,
    val question_text: String,
    val answer: String? = null,
    val answered_at: String? = null,
)

@Serializable
data class InboxEvent(
    val event_id: String,
    val event_type: String,
    val payload: JsonObject? = null,
    val created_at: String? = null,
    val read_at: String? = null,
)
