package com.kilu.pocketagent.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Non-blocking OkHttp suspend extension.
 *
 * Uses OkHttp's enqueue() callback API + suspendCancellableCoroutine.
 * ZERO threads are blocked while waiting for the response — the coroutine
 * suspends and resumes on OkHttp's internal dispatcher thread when the
 * network response arrives.
 *
 * This is safe to call without any withContext(Dispatchers.IO) wrapper,
 * and works correctly even on OEM Android devices that throttle coroutine
 * thread pools (Dispatchers.IO / Dispatchers.Default) in background services.
 */
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        cancel()
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}
