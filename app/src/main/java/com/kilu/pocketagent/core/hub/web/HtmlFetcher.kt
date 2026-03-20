package com.kilu.pocketagent.core.hub.web

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

/**
 * HtmlFetcher — bounded document ingestion for Hub background service.
 *
 * Capability contract:
 *   Hub does NOT render pages.
 *   Hub performs bounded network retrieval and deterministic HTML extraction.
 *
 * THREADING MODEL (v0.9.17):
 *   OkHttp blocking execute() runs on a raw JVM Thread (daemon, NOT any
 *   coroutine dispatcher). This prevents OEM Android thread pool throttling.
 *
 *   Result is delivered via CompletableFuture<T>.
 *   Caller awaits via future.await() — a true non-blocking coroutine suspend
 *   (from kotlinx.coroutines.future). No thread is blocked while waiting.
 *
 *   withTimeout(30_000) at the call site provides hard cancellation if
 *   the raw thread hangs unexpectedly. On cancellation, dispatcher.cancelAll()
 *   kills any in-flight OkHttp connections.
 *
 * Fail-closed policy:
 *   - Content-Type: only text/html and text/xhtml accepted
 *   - Body size: hard cap at MAX_BODY_BYTES (1 MB) before parsing
 *   - Max extracted text: MAX_TEXT_CHARS characters
 *   - JS-rendered content: not supported — static HTML only
 */
class HtmlFetcher {

    companion object {
        private const val MAX_BODY_BYTES        = 1_048_576L    // 1 MB
        private const val MAX_TEXT_CHARS        = 4_000
        private val ALLOWED_CONTENT_TYPES = setOf(
            "text/html", "text/xhtml+xml", "application/xhtml+xml"
        )
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ExtractedContent(
        val paragraphs: String,
        val headings: List<String>,
        val statusCode: Int,
        val finalUrl: String,
        val adapter: String = "okhttp+jsoup",
        val error: String? = null
    )

    /**
     * Fetch [url] — OkHttp runs on raw Thread, result awaited via future.await().
     * Wrap call site with withTimeout(30_000) for hard cancellation.
     */
    suspend fun fetchAndExtract(url: String): ExtractedContent {
        val future = CompletableFuture<ExtractedContent>()

        val thread = Thread {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                    .header("Accept",
                        "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .build()

                val response = client.newCall(request).execute()
                val statusCode = response.code
                val finalUrl   = response.request.url.toString()

                if (statusCode >= 400) {
                    response.close()
                    future.complete(ExtractedContent("", emptyList(), statusCode, finalUrl,
                        error = "HTTP_ERROR_$statusCode"))
                    return@Thread
                }

                val rawCT = response.header("Content-Type") ?: ""
                val baseCT = rawCT.substringBefore(";").trim().lowercase()
                if (baseCT !in ALLOWED_CONTENT_TYPES) {
                    response.close()
                    Log.w("HtmlFetcher", "Rejected Content-Type '$baseCT'")
                    future.complete(ExtractedContent("", emptyList(), statusCode, finalUrl,
                        error = "ERR_CONTENT_TYPE_REJECTED:$baseCT"))
                    return@Thread
                }

                val bodySource = response.body?.source()
                if (bodySource == null) {
                    response.close()
                    future.complete(ExtractedContent("", emptyList(), statusCode, finalUrl,
                        error = "ERR_EMPTY_BODY"))
                    return@Thread
                }

                bodySource.request(MAX_BODY_BYTES)
                val buffered = bodySource.buffer.readByteArray(
                    minOf(bodySource.buffer.size, MAX_BODY_BYTES))
                response.close()

                val charsetName = rawCT.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                    ?.substringAfter("=")?.trim() ?: "UTF-8"

                val htmlString = try { String(buffered, charset(charsetName)) }
                    catch (_: Exception) { String(buffered, Charsets.UTF_8) }

                val doc: Document = Jsoup.parse(htmlString, finalUrl)
                val bodyText = doc.body()?.text() ?: ""

                if (listOf("paywall", "subscribe to read", "subscription required",
                        "premium content", "members only")
                        .any { bodyText.lowercase().contains(it) }) {
                    future.complete(ExtractedContent("", emptyList(), statusCode, finalUrl,
                        error = "PAYWALL_DETECTED"))
                    return@Thread
                }

                val headings = doc.select("h1, h2, h3")
                    .take(10).map { it.text().trim() }.filter { it.isNotBlank() }

                val paragraphText = doc.select("p, article, main, section")
                    .joinToString("\n") { it.text().trim() }
                    .take(MAX_TEXT_CHARS).trim()

                val finalText = if (paragraphText.length < 100) bodyText.take(MAX_TEXT_CHARS).trim()
                    else paragraphText

                Log.d("HtmlFetcher", "OK: ${finalText.length} chars, ${headings.size} h, $finalUrl")
                future.complete(ExtractedContent(finalText, headings, statusCode, finalUrl))

            } catch (e: Exception) {
                Log.e("HtmlFetcher", "Thread error for $url: ${e.message}")
                future.complete(ExtractedContent("", emptyList(), 0, url, error = e.message ?: "ERR_NETWORK"))
            }
        }

        thread.isDaemon = true
        thread.name = "HtmlFetcher"
        thread.start()

        // future.await() is a NON-BLOCKING coroutine suspend — no thread is blocked.
        // It registers a completion callback on the CompletableFuture internally.
        // The coroutine resumes when the raw thread completes the future.
        // withTimeout(30s) at call site cancels this await if the thread hangs.
        return future.await()
    }

    fun cancelAll() {
        client.dispatcher.cancelAll()
    }
}
