package com.kilu.pocketagent.core.crypto

import android.util.Base64
import android.util.Log
import com.kilu.pocketagent.BuildConfig
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object CryptoUtils {
    
    fun verifyServerSig(offerCoreHash: String, sigB64: String?, kid: String?): Boolean {
        if (sigB64 == null) {
            Log.e("CryptoUtils", "No server signature provided.")
            return BuildConfig.FLAVOR == "dev"
        }
        
        // Match kid with BuildConfig
        if (kid != BuildConfig.SERVER_KID) {
            Log.e("CryptoUtils", "Key ID mismatch. Expected ${BuildConfig.SERVER_KID}, got $kid")
            return false
        }
        
        return try {
            val pubBytes = Base64.decode(BuildConfig.SERVER_PUBKEY_B64, Base64.NO_WRAP)
            val pubKey = Ed25519PublicKeyParameters(pubBytes, 0)
            
            val sigBytes = Base64.decode(sigB64, Base64.NO_WRAP)
            val messageBytes = HashingUtil.extractHashBytesToSign(offerCoreHash)
            
            val verifier = Ed25519Signer()
            verifier.init(false, pubKey)
            verifier.update(messageBytes, 0, messageBytes.size)
            verifier.verifySignature(sigBytes)
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Server sig verification exception: \${e.message}")
            false
        }
    }
}
