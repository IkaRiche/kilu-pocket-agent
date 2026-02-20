package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class HubQueueResponse(
    val task_id: String,
    val plan_id: String,
    val grant_id: String,
    val tenant_id: String,
    val external_url: String,
    val expires_at: String,
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
