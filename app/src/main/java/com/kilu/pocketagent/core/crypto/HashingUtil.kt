package com.kilu.pocketagent.core.crypto

import java.security.MessageDigest

object HashingUtil {
    /**
     * Returns the exact bytes the Control Plane uses when signing/verifying:
     *   message = TextEncoder().encode(offer_core_hash)
     * i.e. the UTF-8 bytes of the full string "sha256:<hex>".
     *
     * The server signs this UTF-8 representation, NOT the raw 32-byte digest.
     */
    fun extractHashBytesToSign(offerCoreHash: String): ByteArray {
        require(offerCoreHash.startsWith("sha256:")) {
            "Expected format 'sha256:<hex>', got: ${offerCoreHash.take(20)}"
        }
        return offerCoreHash.toByteArray(Charsets.UTF_8)
    }
}
