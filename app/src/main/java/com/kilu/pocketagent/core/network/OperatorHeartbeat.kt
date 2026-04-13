package com.kilu.pocketagent.core.network

import android.util.Log
import com.kilu.pocketagent.BuildConfig
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.storage.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * OperatorHeartbeat — U1-C1: Sends periodic heartbeat pings to the KiLu
 * Operator Console Worker, enabling truthful 3-state health (online/stale/unknown).
 *
 * Sends two heartbeat types depending on the device role:
 *   - Role.HUB      → component_type = "android_hub"
 *   - Role.APPROVER → component_type = "approver_app"
 *
 * Config is read from BuildConfig:
 *   HEARTBEAT_SECRET  — set as a buildConfigField in build.gradle.kts
 *
 * The Worker URL is read from the same control plane URL already configured
 * in DeviceProfileStore (same host, different path).
 *
 * Usage in HubRuntimeService:
 *   OperatorHeartbeat.startLoop(lifecycleScope, store)
 */
object OperatorHeartbeat {

    private const val TAG = "OperatorHeartbeat"
    private const val INTERVAL_MS = 60_000L   // 60s — well within 120s online threshold
    private const val TTL_SEC     = 270        // 4.5 min — in case of missed beat

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Starts a non-blocking coroutine loop that pings on launch and every 60s.
     * Safe to call from LifecycleService (lifecycleScope).
     * Stops automatically when scope is cancelled.
     */
    fun startLoop(scope: CoroutineScope, store: DeviceProfileStore) {
        val secret = BuildConfig.HEARTBEAT_SECRET
        if (secret.isBlank()) {
            Log.d(TAG, "HEARTBEAT_SECRET not set — loop skipped")
            return
        }

        scope.launch {
            while (isActive) {
                sendPing(store, secret)
                delay(INTERVAL_MS)
            }
        }
    }

    private fun sendPing(store: DeviceProfileStore, secret: String) {
        val role = store.getRole() ?: return
        val componentType = when (role) {
            Role.HUB      -> "android_hub"
            Role.APPROVER -> "approver_app"
        }
        val machineId  = store.getDeviceId() ?: android.os.Build.MODEL
        val workerBase = store.getControlPlaneUrl().trimEnd('/')

        val payload = JSONObject().apply {
            put("component_id",   componentType)     // matches EXPECTED_CATALOG key on Worker
            put("component_type", componentType)
            put("machine_id",     machineId)
            put("status",         "ok")
            put("version",        BuildConfig.VERSION_NAME)
            put("timestamp",      Instant.now().toString())
            put("ttl_sec",        TTL_SEC)
        }

        val request = Request.Builder()
            .url("$workerBase/api/operator/heartbeat")
            .put(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("X-Heartbeat-Secret", secret)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Log.d(TAG, "ok — component_id=$componentType machine=$machineId")
                } else {
                    Log.w(TAG, "server error ${resp.code} for component_id=$componentType")
                }
            }
        } catch (e: Exception) {
            // Network errors must not crash the service
            Log.w(TAG, "network error: ${e.message}")
        }
    }
}
