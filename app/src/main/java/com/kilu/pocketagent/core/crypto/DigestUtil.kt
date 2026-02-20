package com.kilu.pocketagent.core.crypto

import java.security.MessageDigest

object DigestUtil {
    fun sha256Hex(content: String): String {
        val bytes = content.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
