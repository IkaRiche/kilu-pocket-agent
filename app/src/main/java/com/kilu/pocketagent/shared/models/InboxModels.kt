package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class InboxEpisode(
    val episode_id: String,
    val task_id: String,
    val event_type: String, // RESULT_READY, ASSUMPTIONS_REQUIRED, PLAN_APPROVAL_REQUIRED
    val created_at: String,
    val requires_ack: Boolean,
    val payload: JsonObject? = null
)

@Serializable
data class ResultPayloadView(
    val url: String,
    val extracted_text: String,
    val summary: String
)
