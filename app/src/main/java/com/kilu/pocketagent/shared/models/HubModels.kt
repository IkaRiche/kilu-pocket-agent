package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class HubQueueListResponse(
    val items: List<HubQueueResponse> = emptyList()
)

@Serializable
data class HubQueueResponse(
    val task_id: String,
    val plan_id: String? = null,
    val active_grant_id: String? = null,
    val grant_id: String? = null,
    val tenant_id: String? = null,
    val inputs: String? = null,
    val external_url: String? = null,
    val expires_at: String? = null,
    val limits: JsonObject? = null,
    val forbidden: JsonObject? = null,
    val toolchain_id: String? = null,         // grant-bound toolchain — use for mintStepBatch
    val target_runtime_id: String? = null     // grant-bound runtime — use for mintStepBatch
)

@Serializable
data class StepInfo(
    val step_type: String = "BROWSER",   // required; NO step_id — server has additionalProperties:false
    val step_digest: String              // must be "sha256:<64 hex chars>"
)

@Serializable
data class StepToken(
    val token_id: String,
    val jti: String,
    val exp: String,
    val claims_jcs: String,
    val sig_b64: String
)

@Serializable
data class MintStepBatchReq(
    val runtime_id: String,
    val toolchain_id: String,
    val steps: List<StepInfo>
)

@Serializable
data class MintStepBatchResp(
    val tokens: List<StepToken>   // server returns { tokens: [...] }
)

@Serializable
data class Evidence(
    val task_id: String,
    val step_id: String,
    val runner_id: String,
    val adapter: String,
    val outcome: String,
    val normalized_action: String? = null,
    val receipt_ref: String? = null,
    val started_at: String,
    val finished_at: String,
    val exit_code: Int? = null,
    val stdout_hash: String? = null,
    val stderr_hash: String? = null,
    val stdout_truncated: Boolean = false,
    val stderr_truncated: Boolean = false,
    val timeout: Boolean = false,
    val cancelled: Boolean = false,
    val error_class: String? = null,
    val error_message: String? = null
)

@Serializable
data class ExecutionResult(
    val url: String? = null,
    val final_url: String? = null,
    val summary: String? = null,
    val headings: List<String>? = null,
    val extracted_text_preview: String? = null,
    val content_type: String? = null
)

@Serializable
data class SubmitResultReq(
    val evidence: Evidence,
    val result: ExecutionResult? = null  // semantic output for operator visibility
)


@Serializable
data class AssumptionItemReq(
    val key: String,
    val question: String
)

@Serializable
data class RequestAssumptionsReq(
    val assumptions: List<AssumptionItemReq>
)
