package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApproverTaskItem(
    val task_id: String,
    val external_url: String,
    val status: String,
    val created_at: String
)

@Serializable
data class CreateTaskReq(
    val url: String,
    val report_style: String = "short"
)

@Serializable
data class CreateTaskResp(
    val task_id: String,
    val status: String,
    val created_at: String
)

@Serializable
data class PlanPreviewResp(
    val plan_id: String,
    val max_steps: Int,
    val expires_at: String,
    val allowlist_domains: List<String>,
    val forbidden_flags: JsonObject
)

@Serializable
data class ApprovePlanReq(
    val pubkey_alg: String = "ED25519",
    val pubkey_b64: String,
    val signature_b64: String
)

@Serializable
data class ApprovePlanResp(
    val grant_id: String,
    val status: String
)
