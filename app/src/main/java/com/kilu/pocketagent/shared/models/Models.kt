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

@Serializable
data class ApproverConfirmReq(
    val pairing_token: String,
    val display_name: String,
    val pubkey_alg: String = "ED25519",
    val pubkey_b64: String,
    val signature_b64: String
)

@Serializable
data class ApproverConfirmResp(
    val tenant_id: String,
    val device_id: String,
    val device_session_token: String
)

@Serializable
data class HubConfirmReq(
    val hub_link_code: String,
    val display_name: String,
    val pubkey_alg: String = "ED25519",
    val pubkey_b64: String,
    val signature_b64: String,
    val toolchain_id: String = "tc_android_v1"
)

@Serializable
data class HubConfirmResp(
    val tenant_id: String,
    val device_id: String,
    val runtime_id: String,
    val toolchain_id: String,
    val hub_session_token: String
)
