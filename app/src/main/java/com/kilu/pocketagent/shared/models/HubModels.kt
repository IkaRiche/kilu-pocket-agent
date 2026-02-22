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
    val forbidden: JsonObject? = null
)

@Serializable
data class MintStepBatchReq(
    val size: Int = 1
)

@Serializable
data class MintStepBatchResp(
    val batch_id: String,
    val size: Int,
    val issued_at: String
)

@Serializable
data class SubmitResultReq(
    val url: String,
    val extracted_text: String,
    val headings: List<String> = emptyList(),
    val summary: String = "",
    val facts: List<String> = emptyList(),
    val hashes: JsonObject
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
