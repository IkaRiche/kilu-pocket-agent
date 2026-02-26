package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class InboxResponse(
    val events: List<InboxEvent> = emptyList()
)

@Serializable
data class InboxEvent(
    val event_id: String,
    val event_type: String, // RESULT_READY, ASSUMPTIONS_REQUIRED, PLAN_APPROVAL_REQUIRED
    val created_at: String,
    val payload: JsonObject? = null
)

// Legacy alias for backwards compatibility
typealias InboxEpisode = InboxEvent

@Serializable
data class ResultPayloadView(
    val url: String,
    val extracted_text: String,
    val summary: String
)
