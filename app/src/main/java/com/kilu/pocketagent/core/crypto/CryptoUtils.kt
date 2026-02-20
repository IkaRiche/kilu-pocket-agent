package com.kilu.pocketagent.core.crypto

import android.util.Log

object CryptoUtils {
    fun verifyServerSig(payload: String, sigHex: String?): Boolean {
        // TODO: implement actual verification using Ed25519 Keystore alias
        Log.d("CryptoUtils", "verifyServerSig: stub called. Returning true.")
        return true
    }
}
