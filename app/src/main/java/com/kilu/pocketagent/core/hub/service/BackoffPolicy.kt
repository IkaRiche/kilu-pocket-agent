package com.kilu.pocketagent.core.hub.service

import kotlin.math.min
import kotlin.random.Random

enum class BackoffState {
    IDLE,
    WAITING_APPROVER,
    ERROR_AUTH,
    ERROR_QUOTA,
    ERROR_NETWORK,
    ERROR_UNKNOWN
}

class BackoffPolicy {
    private var idleAttempts = 0
    private var errorAttempts = 0

    fun getDelayMs(state: BackoffState): Long {
        return when (state) {
            BackoffState.IDLE -> {
                // 5s -> 15s -> 45s -> 2m -> 5m (cap)
                val base = 5000L
                val mult = Math.pow(3.0, idleAttempts.toDouble()).toLong()
                val target = min(base * mult, 300_000L)
                idleAttempts++
                addJitter(target)
            }
            BackoffState.WAITING_APPROVER -> {
                // Fixed 60-120s jittered, cap 10m
                addJitter(90_000L, 30_000L)
            }
            BackoffState.ERROR_AUTH -> 300_000L // 5 minutes flat cap for bad session
            BackoffState.ERROR_QUOTA -> 120_000L // 2 minutes for 429 mint-step-batch
            BackoffState.ERROR_NETWORK -> {
                // 15s -> 30s -> 60s
                val base = 15000L
                val mult = Math.pow(2.0, errorAttempts.toDouble()).toLong()
                errorAttempts++
                addJitter(min(base * mult, 300_000L))
            }
            BackoffState.ERROR_UNKNOWN -> {
                // 30s -> 2m -> 10m cap
                val base = 30000L
                val mult = Math.pow(4.0, errorAttempts.toDouble()).toLong()
                errorAttempts++
                addJitter(min(base * mult, 600_000L))
            }
        }
    }

    fun reset() {
        idleAttempts = 0
        errorAttempts = 0
    }

    private fun addJitter(baseDelay: Long, jitterMax: Long = 2000L): Long {
        val jitter = Random.nextLong(-jitterMax, jitterMax)
        return (baseDelay + jitter).coerceAtLeast(1000L) // Minimum 1 second
    }
}
