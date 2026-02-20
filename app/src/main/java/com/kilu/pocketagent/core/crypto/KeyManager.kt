package com.kilu.pocketagent.core.crypto

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kilu.pocketagent.core.storage.Role
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import android.util.Base64

/**
 * KeyManager v0
 * Handles the generation, storage, and signing capabilities of Ed25519 keys.
 * V0 Fallback: Uses BouncyCastle Ed25519 over EncryptedSharedPreferences if true Keystore Ed25519
 * bindings are unavailable or too fragmented across Android 8-11 devices.
 */
class KeyManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kilu_keys_v0",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun ensureKey(role: Role) {
        val alias = getAlias(role)
        if (!prefs.contains("${alias}_priv")) {
            val random = SecureRandom()
            val generator = Ed25519KeyPairGenerator()
            generator.init(Ed25519KeyGenerationParameters(random))
            val pair = generator.generateKeyPair()
            
            val priv = pair.private as Ed25519PrivateKeyParameters
            val pub = pair.public as Ed25519PublicKeyParameters

            prefs.edit()
                .putString("${alias}_priv", Base64.encodeToString(priv.encoded, Base64.NO_WRAP))
                .putString("${alias}_pub", Base64.encodeToString(pub.encoded, Base64.NO_WRAP))
                .apply()
            
            Log.d("KeyManager", "Ensured new KeyPair for alias: $alias")
        }
    }

    fun publicKey(role: Role): String {
        return prefs.getString("${getAlias(role)}_pub", null) 
            ?: throw IllegalStateException("Key not found for $role")
    }

    fun sign(role: Role, message: ByteArray): String {
        val privStr = prefs.getString("${getAlias(role)}_priv", null)
            ?: throw IllegalStateException("Key not found for $role")
        
        val privBytes = Base64.decode(privStr, Base64.NO_WRAP)
        val privKey = Ed25519PrivateKeyParameters(privBytes, 0)
        
        val signer = Ed25519Signer()
        signer.init(true, privKey)
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()
        
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun wipeKeys(role: Role) {
        val alias = getAlias(role)
        prefs.edit()
            .remove("${alias}_priv")
            .remove("${alias}_pub")
            .apply()
        Log.d("KeyManager", "Wiped key pair for role: $role")
    }

    private fun getAlias(role: Role) = "kilu_ed25519_${role.name.lowercase()}"
}
