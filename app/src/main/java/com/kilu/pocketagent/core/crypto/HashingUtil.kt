package com.kilu.pocketagent.core.crypto

import java.security.MessageDigest

object HashingUtil {
    /**
     * Recreates the exact sha-256 string expected by the Control Plane.
     * The input is the raw hash string sent by the server (e.g., "sha256:abcd...").
     * The client must extract the "abcd..." hex digest, convert it back to bytes,
     * so that the Ed25519 algorithm signs the exact byte array representation.
     */
    fun extractHashBytesToSign(offerCoreHash: String): ByteArray {
        if (!offerCoreHash.startsWith("sha256:")) {
            throw IllegalArgumentException("Expected format 'sha256:<hex>'")
        }
        val hex = offerCoreHash.substringAfter("sha256:")
        return hexStringToByteArray(hex)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
