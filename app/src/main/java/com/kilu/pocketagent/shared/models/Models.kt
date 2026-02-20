package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class QRPayload(
    val cp: String,
    val t: String,
    val h: String,
    val e: String
)

@Serializable
data class PairingInitResponse(
    val pairing_token: String,
    val offer_core_hash: String,
    val short_code: String? = null,
    val server_sig: String? = null,
    val expires_at: String
)
