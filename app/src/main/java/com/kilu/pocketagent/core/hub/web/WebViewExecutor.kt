package com.kilu.pocketagent.core.hub.web

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class WebViewExecutor(private val context: Context) {
    private var webView: WebView? = null
    private var currentHttpError: String? = null
    
    // A2: State Machine
    enum class ExecutionState {
        IDLE, LOAD_START, DOM_READY, EXTRACT, DONE, FAILED
    }
    var state = ExecutionState.IDLE
        private set

    suspend fun initialize() {
        withContext(Dispatchers.Main) {
            if (webView == null) {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    
                    // A1. Minimal Sandboxing Defaults
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
        }
    }

    suspend fun destroy() {
        withContext(Dispatchers.Main) {
            webView?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                removeAllViews()
                destroy()
            }
            webView = null
            state = ExecutionState.IDLE
        }
    }

    /**
     * Loads the URL and waits for onPageFinished (DOM_READY)
     * Throws an Exception if an HTTP auth/paywall error is encountered.
     * Uses granular timeouts logic. (A3)
     */
    suspend fun loadUrl(url: String, pageLoadTimeoutMs: Long = 15000, domReadyTimeoutMs: Long = 5000): Result<Unit> = withContext(Dispatchers.Main) {
        if (webView == null) return@withContext Result.failure(IllegalStateException("WebView not initialized"))
        
        currentHttpError = null
        state = ExecutionState.LOAD_START

        // Suspend until page is loaded
        suspendCancellableCoroutine<Result<Unit>> { continuation ->
            var finished = false
            
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val lowerUrl = url?.toLowerCase() ?: ""
                    if (lowerUrl.contains("/login") || lowerUrl.contains("/signin") || lowerUrl.contains("/auth")) {
                        currentHttpError = "URL redirect suggests Auth: \$url"
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!finished) {
                        finished = true
                        state = ExecutionState.DOM_READY
                        // Wait an extra layout tick just to be safe for SPA framework hydrates
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(domReadyTimeoutMs)
                            if (currentHttpError != null) {
                                state = ExecutionState.FAILED
                                continuation.resume(Result.failure(Exception(currentHttpError)))
                            } else {
                                state = ExecutionState.EXTRACT
                                continuation.resume(Result.success(Unit))
                            }
                        }
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
                        if (code == 401 || code == 403 || code == 429) {
                            currentHttpError = "HTTP Error \$code on main frame"
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        currentHttpError = "Page Load Error: \${error?.errorCode}"
                    }
                }
            }

            webView?.loadUrl(url)

            CoroutineScope(Dispatchers.Main).launch {
                delay(pageLoadTimeoutMs)
                if (!finished) {
                    finished = true
                    webView?.stopLoading()
                    state = ExecutionState.FAILED
                    continuation.resume(Result.failure(Exception("pageLoadTimeout of \${pageLoadTimeoutMs}ms exceeded. Site might be blocked.")))
                }
            }
        }
    }

    // A3: jsEvalTimeout added
    suspend fun evaluateJavascript(script: String, jsEvalTimeoutMs: Long = 3000): String = withContext(Dispatchers.Main) {
        val wv = webView ?: throw IllegalStateException("WebView not initialized")
        suspendCancellableCoroutine { continuation ->
            var resolved = false
            
            wv.evaluateJavascript(script) { result ->
                if (!resolved) {
                    resolved = true
                    val cleaned = if (result != null && result.length >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
                        result.substring(1, result.length - 1)
                    } else {
                        result ?: ""
                    }
                    val unescaped = cleaned.replace("\\\\u([0-9A-Fa-f]{4})".toRegex()) {
                        it.groupValues[1].toInt(16).toChar().toString()
                    }.replace("\\\\n", "\n").replace("\\\\\"", "\"")
                    
                    continuation.resume(unescaped)
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                delay(jsEvalTimeoutMs)
                if (!resolved) {
                    resolved = true
                    continuation.resume("") // Return empty on eval timeout
                }
            }
        }
    }
}
