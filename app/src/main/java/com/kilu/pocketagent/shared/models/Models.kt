package com.kilu.pocketagent.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class QRPayload(
    val cp: String,
    val t: String,
    val h: String,
    val e: String,
    val ss: String? = null,
    val kid: String? = null
)

@Serializable
data class PairingInitResponse(
    val pairing_token: String? = null,
    val hub_link_code: String? = null,
    val offer_core_hash: String? = null,
    val expires_at: String? = null,
    val qr_payload: ServerQRPayload? = null,
    val server_sig: String? = null, // Compatibility
    val short_code: String? = null
) {
    fun getEffectiveHash(): String = offer_core_hash 
        ?: qr_payload?.offer_core_hash 
        ?: ""

    fun getEffectiveExpiresAt(): String = expires_at 
        ?: qr_payload?.offer_core?.expires_at 
        ?: ""
    
    fun getEffectiveToken(): String = pairing_token 
        ?: hub_link_code 
        ?: qr_payload?.offer_core?.pairing_token 
        ?: qr_payload?.offer_core?.hub_link_code
        ?: ""
}

@Serializable
data class ServerQRPayload(
    val offer_core: ServerOfferCore,
    val offer_core_hash: String,
    val server_sig: ServerSig? = null
)

@Serializable
data class ServerOfferCore(
    val pairing_token: String? = null,
    val hub_link_code: String? = null,
    val expires_at: String
)

@Serializable
data class ServerSig(
    val alg: String,
    val sig_b64: String
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

@Serializable
data class ErrorEnvelope(
    val error: String,
    val message: String? = null
)
