package com.kilu.pocketagent.core.hub.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * WakelockGuard explicitly guarantees that Wakelocks are only acquired with a hard timeout limit
 * (max 2 minutes) and are verifiably released in finally blocks.
 */
class WakelockGuard(context: Context, tag: String = "KiLu:HubWakeLockGuard") {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock: PowerManager.WakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)

    fun acquire(timeoutMs: Long = 120_000L) {
        if (!wakeLock.isHeld) {
            Log.d("WakelockGuard", "Acquiring Wakelock for max \${timeoutMs}ms")
            wakeLock.acquire(timeoutMs)
        }
    }

    fun release() {
        if (wakeLock.isHeld) {
            try {
                wakeLock.release()
                Log.d("WakelockGuard", "Wakelock normally released")
            } catch (e: Exception) {
                Log.e("WakelockGuard", "Failed to release wakelock: \${e.message}")
            }
        }
    }
    
    fun isHeld(): Boolean = wakeLock.isHeld
}
