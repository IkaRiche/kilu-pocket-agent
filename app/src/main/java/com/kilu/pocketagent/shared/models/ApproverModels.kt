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
    val stdout_truncated: Boolean? = null,
    val timeout: Boolean? = null,
    val cancelled: Boolean? = null
)

@Serializable
data class TaskResult(
    val url: String? = null,
    val final_url: String? = null,
    val summary: String? = null,
    val headings: List<String>? = null,
    val content_type: String? = null,
    val extracted_text_preview: String? = null
)

@Serializable
data class TaskResultPayload(
    val evidence: TaskEvidence? = null,
    val result: TaskResult? = null
)

@Serializable
data class TaskFailure(
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class TaskDetail(
    val task_id: String,
    val tenant_id: String? = null,
    val status: String,
    val title: String? = null,
    val user_prompt: String? = null,
    val executor_preference: String? = null,
    val target_runtime_id: String? = null,
    val plan_id: String? = null,
    val active_grant_id: String? = null,
    val planner_mode: String? = null,
    val result: TaskResultPayload? = null,
    val failure: TaskFailure? = null,
    val version: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    /** Duration in ms between evidence started_at and finished_at, or null if unavailable */
    fun executionDurationMs(): Long? {
        val s = result?.evidence?.started_at ?: return null
        val f = result.evidence.finished_at ?: return null
        return try {
            val fmt = java.time.format.DateTimeFormatter.ISO_DATE_TIME
            val start = java.time.Instant.from(fmt.parse(s))
            val end   = java.time.Instant.from(fmt.parse(f))
            java.time.Duration.between(start, end).toMillis()
        } catch (_: Exception) { null }
    }

    /** Short human-readable duration string, e.g. "1.28 s" */
    fun durationLabel(): String? = executionDurationMs()?.let {
        if (it < 1000) "${it}ms" else "${"%.2f".format(it / 1000.0)}s"
    }
}


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
