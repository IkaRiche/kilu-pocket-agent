package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApproverTaskItem(
    val task_id: String,
    val title: String? = null,
    val user_prompt: String? = null,
    val status: String,
    val created_at: String,
    val final_report_status: String? = "PENDING",
    val final_report: String? = null
)

@Serializable
data class CreateTaskReq(
    val title: String,
    val user_prompt: String,
    val executor_preference: String = "HUB_PREFERRED",
    val target_runtime_id: String? = null,  // 10E: explicit hub binding; null = server chooses
    val skill_id: String? = null,
    val inputs: Map<String, String>? = null
)

@Serializable
data class CreateTaskResp(
    val task_id: String,
    val status: String,
    val target_runtime_id: String? = null,  // 10E: hub that was auto-selected or explicitly bound
    val version: Int = 0
)

/**
 * Enriched device item from GET /v1/devices.
 * Hub devices include runtime_id and related fields for explicit hub selection.
 */
@Serializable
data class HubDevice(
    val device_id: String,
    val device_type: String,
    val display_name: String,
    val status: String,
    val last_seen_at: String? = null,
    // Hub runtime fields (null for APPROVER devices)
    val runtime_id: String? = null,
    val toolchain_id: String? = null,
    val runtime_status: String? = null,
    val runtime_last_seen: String? = null
)

@Serializable
data class PlanStep(
    val op: String,
    val desc: String
)

@Serializable
data class PlanPreviewResp(
    val plan_id: String,
    val max_steps: Int,
    val expires_at: String,
    val allowlist_domains: List<String>,
    val forbidden_flags: JsonObject,
    val summary: String? = null,
    val steps_preview: List<PlanStep>? = null,
    val runtime_id: String? = null,
    val toolchain_id: String? = null,
    val hub_name: String? = null
)

@Serializable
data class QuotasResp(
    val tier: String,
    val planner_credits: Int,
    val report_credits: Int,
    val calls_today: Int,
    val daily_limit: Int
)

@Serializable
data class TaskReportResp(
    val status: String,
    val summary: String? = null
)

@Serializable
data class ApprovalReceipt(
    val pubkey_alg: String,
    val pubkey_b64: String,
    val signature_b64: String
)

@Serializable
data class ApprovePlanReq(
    val device_id: String,
    val biometric_present: Boolean,
    val approval_receipt: ApprovalReceipt
)

@Serializable
data class ApprovePlanResp(
    val grant_id: String,
    val status: String
)
