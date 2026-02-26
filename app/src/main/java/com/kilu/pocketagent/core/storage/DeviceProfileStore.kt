package com.kilu.pocketagent.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kilu.pocketagent.BuildConfig

enum class Role { APPROVER, HUB }

class DeviceProfileStore(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kilu_profile",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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

    fun getControlPlaneUrl(): String = prefs.getString("controlPlaneUrl", BuildConfig.DEFAULT_CONTROL_PLANE_URL) ?: BuildConfig.DEFAULT_CONTROL_PLANE_URL
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

    fun clearPairing() {
        prefs.edit()
            .remove("deviceId")
            .remove("tenantId")
            .remove("sessionToken")
            .apply()
    }
}
