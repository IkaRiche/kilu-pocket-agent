package com.kilu.pocketagent.core.storage

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kilu.pocketagent.BuildConfig

enum class Role { APPROVER, HUB }

class DeviceProfileStore(private val context: Context) {

    private val prefs = createPrefs()

    /**
     * Creates EncryptedSharedPreferences with MasterKey recovery.
     *
     * Problem: After app reinstall, Android Keystore deletes the MasterKey but
     * the encrypted prefs file "kilu_profile.xml" survives on disk. Creating
     * EncryptedSharedPreferences with a new MasterKey throws SecurityException
     * (or causes silent null returns) because the old ciphertext cannot be
     * decrypted by the new key.
     *
     * Fix: On any Exception during creation, delete the corrupted prefs file
     * and retry once with a fresh store. Pairing data is lost (expected after
     * reinstall — the user re-pairs anyway).
     */
    private fun createPrefs() = try {
        buildPrefs()
    } catch (e: Exception) {
        Log.w("DeviceProfileStore",
            "EncryptedSharedPreferences failed (likely MasterKey mismatch after reinstall). " +
            "Wiping kilu_profile and recreating. Error: ${e.message}")
        // Delete the corrupted prefs file
        context.deleteSharedPreferences("kilu_profile")
        // Retry with a clean slate
        try {
            buildPrefs()
        } catch (e2: Exception) {
            Log.e("DeviceProfileStore", "Retry also failed: ${e2.message}")
            // Last resort: plain (unencrypted) fallback so app doesn't crash
            context.getSharedPreferences("kilu_profile_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun buildPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "kilu_profile",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getRole(): Role? = prefs.getString("role", null)?.let { Role.valueOf(it) }
    fun setRole(role: Role) {
        val oldRole = getRole()
        if (oldRole != null && oldRole != role) {
            clearPairing()
            try {
                com.kilu.pocketagent.core.crypto.KeyManager(context).wipeKeys(oldRole)
            } catch (e: Exception) {}
        }
        prefs.edit().putString("role", role.name).apply()
    }

    fun getControlPlaneUrl(): String =
        prefs.getString("controlPlaneUrl", BuildConfig.DEFAULT_CONTROL_PLANE_URL)
            ?: BuildConfig.DEFAULT_CONTROL_PLANE_URL
    fun setControlPlaneUrl(url: String) = prefs.edit().putString("controlPlaneUrl", url).apply()

    fun getDeviceId(): String? = prefs.getString("deviceId", null)
    fun setDeviceId(id: String) = prefs.edit().putString("deviceId", id).apply()

    fun getTenantId(): String? = prefs.getString("tenantId", null)
    fun setTenantId(id: String) = prefs.edit().putString("tenantId", id).apply()

    fun getSessionToken(): String? = prefs.getString("sessionToken", null)
    fun setSessionToken(token: String) = prefs.edit().putString("sessionToken", token).apply()
    fun clearSessionToken() = prefs.edit().remove("sessionToken").apply()

    fun getKeyAlias(): String = prefs.getString("keyAlias", "kilu_default_key")!!
    fun setKeyAlias(alias: String) = prefs.edit().putString("keyAlias", alias).apply()

    fun getRuntimeId(): String? = prefs.getString("runtimeId", null)
    fun setRuntimeId(id: String) = prefs.edit().putString("runtimeId", id).apply()

    fun getToolchainId(): String? = prefs.getString("toolchainId", null)
    fun setToolchainId(id: String) = prefs.edit().putString("toolchainId", id).apply()

    fun clearPairing() {
        prefs.edit()
            .remove("deviceId")
            .remove("tenantId")
            .remove("sessionToken")
            .remove("runtimeId")
            .remove("toolchainId")
            .apply()
    }
}
