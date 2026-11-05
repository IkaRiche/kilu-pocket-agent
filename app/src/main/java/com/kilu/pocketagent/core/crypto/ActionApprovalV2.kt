package com.kilu.pocketagent.core.crypto

import java.security.MessageDigest

/**
 * CREG-003 experimental approval, NOT the historical "receipt:<plan_id>".
 *
 * The exact server-supplied task-input JSON bytes are displayed on the existing
 * PlanPreviewScreen. Approver signs a length-framed, domain-separated message
 * binding plan, AVO digest, action digest, runtime, and toolchain.
 *
 * This verifies exact bytes, not the semantic correctness of the action.
 */
object ActionApprovalV2 {
    const val PROTOCOL = "KILU_APPROVAL_V2"

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun requireActionBinding(actionPayloadJson: String?, actionDigest: String?) {
        require(!actionPayloadJson.isNullOrBlank()) { "Exact action payload is missing" }
        require(!actionDigest.isNullOrBlank()) { "Exact action digest is missing" }
        require(Regex("^sha256:[0-9a-f]{64}$").matches(actionDigest)) { "Invalid action digest format" }
        require(sha256(actionPayloadJson) == actionDigest) {
            "Displayed action bytes differ from server-signed action digest"
        }
    }

    fun message(
        planId: String,
        avoHash: String,
        actionDigest: String,
        runtimeId: String,
        toolchainId: String,
    ): ByteArray {
        val fields = listOf(planId, avoHash, actionDigest, runtimeId, toolchainId)
        require(fields.none { it.isBlank() }) { "Incomplete approval binding" }
        require(Regex("^sha256:[0-9a-f]{64}$").matches(avoHash)) { "Invalid AVO hash" }
        require(Regex("^sha256:[0-9a-f]{64}$").matches(actionDigest)) { "Invalid action hash" }
        val framed = fields.joinToString("") {
            val bytes = it.toByteArray(Charsets.UTF_8)
            "${bytes.size}:$it"
        }
        return "$PROTOCOL:$framed".toByteArray(Charsets.UTF_8)
    }
}
