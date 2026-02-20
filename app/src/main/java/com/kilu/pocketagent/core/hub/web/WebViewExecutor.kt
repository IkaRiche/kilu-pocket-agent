package com.kilu.pocketagent.core.hub.web

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebViewExecutor(private val context: Context) {
    private var webView: WebView? = null
    private var currentHttpError: String? = null

    suspend fun initialize() {
        withContext(Dispatchers.Main) {
            if (webView == null) {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                }
            }
        }
    }

    /**
     * Loads the URL and waits for onPageFinished or timeout.
     * Throws an Exception if an HTTP auth/paywall error is encountered.
     */
    suspend fun loadUrl(url: String, timeoutMs: Long = 15000): Result<Unit> = withContext(Dispatchers.Main) {
        if (webView == null) return@withContext Result.failure(IllegalStateException("WebView not initialized"))
        
        currentHttpError = null

        // Suspend until page is loaded
        suspendCancellableCoroutine<Result<Unit>> { continuation ->
            var finished = false
            
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // Basic url checks
                    val lowerUrl = url?.toLowerCase() ?: ""
                    if (lowerUrl.contains("/login") || lowerUrl.contains("/signin") || lowerUrl.contains("/auth")) {
                        currentHttpError = "URL redirect suggests Auth: \$url"
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!finished) {
                        finished = true
                        if (currentHttpError != null) {
                            continuation.resume(Result.failure(Exception(currentHttpError)))
                        } else {
                            continuation.resume(Result.success(Unit))
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

            // Setup timeout
            CoroutineScope(Dispatchers.Main).launch {
                delay(timeoutMs)
                if (!finished) {
                    finished = true
                    webView?.stopLoading()
                    // Timeout -> assume fail-closed (Blocked or Interactive)
                    continuation.resume(Result.failure(Exception("Timeout loading DOM. Site might be blocked or interactive.")))
                }
            }
        }
    }

    suspend fun evaluateJavascript(script: String): String = withContext(Dispatchers.Main) {
        val wv = webView ?: throw IllegalStateException("WebView not initialized")
        suspendCancellableCoroutine { continuation ->
            wv.evaluateJavascript(script) { result ->
                // The result is usually JSON stringified or raw primitive.
                // We strip quotes if it's a simple string, else return literal.
                val cleaned = if (result != null && result.length >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
                    result.substring(1, result.length - 1)
                } else {
                    result ?: ""
                }
                // Handle unicode escapes
                val unescaped = cleaned.replace("\\\\u([0-9A-Fa-f]{4})".toRegex()) {
                    it.groupValues[1].toInt(16).toChar().toString()
                }.replace("\\\\n", "\n").replace("\\\\\"", "\"")
                
                continuation.resume(unescaped)
            }
        }
    }
}
