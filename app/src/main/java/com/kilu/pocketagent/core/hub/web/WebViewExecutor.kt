package com.kilu.pocketagent.core.hub.web

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

/**
 * WebViewExecutor — headless WebView wrapper for background service execution.
 *
 * KEY DESIGN CONSTRAINT:
 * We must NOT use withContext(Dispatchers.Main) as a suspension wrapper because:
 * - If called from a coroutine running on Dispatchers.IO, the Main dispatcher is not
 *   blocked (good), but the timeout coroutine ALSO dispatches to Main (deadlock on some paths).
 * - The correct pattern for service-context WebView is to use Handler(Looper.getMainLooper())
 *   directly for WebView operations, and CompletableFuture / CompletableDeferred for results.
 *
 * This avoids all dispatcher-level deadlocks.
 */
class WebViewExecutor(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var currentHttpError: String? = null

    enum class ExecutionState {
        IDLE, LOAD_START, DOM_READY, EXTRACT, DONE, FAILED
    }
    var state = ExecutionState.IDLE
        private set

    /**
     * Initialize WebView on the Main thread via Handler.
     * This method is safe to call from any coroutine context.
     */
    suspend fun initialize() {
        val future = CompletableFuture<Unit>()
        mainHandler.post {
            try {
                if (webView == null) {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                    }
                }
                future.complete(Unit)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        future.await()
    }

    suspend fun destroy() {
        val future = CompletableFuture<Unit>()
        mainHandler.post {
            webView?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                removeAllViews()
                destroy()
            }
            webView = null
            state = ExecutionState.IDLE
            future.complete(Unit)
        }
        future.await()
    }

    /**
     * Load a URL and wait for page load to complete.
     * Uses CompletableFuture + Handler — no coroutine dispatcher blocking.
     * Timeout runs on a separate thread pool (not Main), so it always fires.
     */
    suspend fun loadUrl(url: String, pageLoadTimeoutMs: Long = 15000L, domReadyTimeoutMs: Long = 5000L): Result<Unit> {
        val future = CompletableFuture<Result<Unit>>()
        currentHttpError = null
        state = ExecutionState.LOAD_START

        mainHandler.post {
            val wv = webView
            if (wv == null) {
                future.complete(Result.failure(IllegalStateException("WebView not initialized")))
                return@post
            }

            var finished = false

            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // No URL-based auth check — too broad, causes false positives
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!finished) {
                        finished = true
                        state = ExecutionState.DOM_READY
                        // Wait domReadyTimeoutMs for SPA hydration, then resolve
                        mainHandler.postDelayed({
                            if (!future.isDone) {
                                if (currentHttpError != null) {
                                    state = ExecutionState.FAILED
                                    future.complete(Result.failure(Exception(currentHttpError)))
                                } else {
                                    state = ExecutionState.EXTRACT
                                    future.complete(Result.success(Unit))
                                }
                            }
                        }, domReadyTimeoutMs)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        val code = errorResponse?.statusCode ?: 0
                        currentHttpError = "HTTP Error $code on main frame"
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        currentHttpError = "Page Load Error: ${error?.errorCode}"
                    }
                }
            }

            wv.loadUrl(url)

            // Timeout: runs on a background thread — NOT on Main — so it always fires
            Thread {
                Thread.sleep(pageLoadTimeoutMs)
                if (!finished && !future.isDone) {
                    finished = true
                    mainHandler.post {
                        wv.stopLoading()
                        state = ExecutionState.FAILED
                    }
                    future.complete(Result.failure(Exception("pageLoadTimeout of ${pageLoadTimeoutMs}ms exceeded.")))
                }
            }.start()
        }

        return future.await()
    }

    /**
     * Evaluate JavaScript and return the result string.
     * Uses CompletableFuture — safe from any coroutine context.
     */
    suspend fun evaluateJavascript(script: String, jsEvalTimeoutMs: Long = 3000L): String {
        val future = CompletableFuture<String>()

        mainHandler.post {
            val wv = webView
            if (wv == null) {
                future.complete("")
                return@post
            }

            var resolved = false
            wv.evaluateJavascript(script) { result ->
                if (!resolved) {
                    resolved = true
                    val cleaned = if (result != null && result.length >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
                        result.substring(1, result.length - 1)
                    } else {
                        result ?: ""
                    }
                    val unescaped = cleaned
                        .replace("\\u([0-9A-Fa-f]{4})".toRegex()) {
                            it.groupValues[1].toInt(16).toChar().toString()
                        }
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                    future.complete(unescaped)
                }
            }

            // Timeout on background thread — always fires
            Thread {
                Thread.sleep(jsEvalTimeoutMs)
                if (!resolved) {
                    resolved = true
                    future.complete("")
                }
            }.start()
        }

        return future.await()
    }
}
