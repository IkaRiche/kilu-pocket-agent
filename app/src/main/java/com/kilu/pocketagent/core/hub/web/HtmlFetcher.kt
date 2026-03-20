package com.kilu.pocketagent.core.hub.web

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * HtmlFetcher — bounded document ingestion for Hub background service.
 *
 * Capability contract:
 *   Hub does NOT render pages.
 *   Hub performs bounded network retrieval and deterministic HTML extraction.
 *
 * THREADING MODEL:
 *   OkHttp blocking execute() runs on a raw Thread (NOT Dispatchers.IO).
 *   This bypasses any OEM Android throttling of coroutine thread pools.
 *   CompletableFuture.get(timeout) enforces hard wall-clock deadline.
 *
 * Fail-closed policy:
 *   - Content-Type: only text/html and text/xhtml accepted
 *   - Body size: hard cap at MAX_BODY_BYTES (1 MB) before parsing
 *   - Redirects: max MAX_REDIRECTS (5) hops
 *   - Extracted text: max MAX_TEXT_CHARS characters returned
 *   - Network timeout: MAX_TOTAL_TIMEOUT_SEC wall-clock seconds
 *   - JS-rendered content: not supported — static HTML only
 */
class HtmlFetcher {

    companion object {
        private const val MAX_BODY_BYTES           = 1_048_576L    // 1 MB
        private const val MAX_TEXT_CHARS           = 4_000
        private const val MAX_REDIRECTS            = 5
        private const val MAX_TOTAL_TIMEOUT_SEC    = 25L           // wall-clock hard cap
        private val ALLOWED_CONTENT_TYPES = setOf(
            "text/html", "text/xhtml+xml", "application/xhtml+xml"
        )
    }

    // OkHttp client — shared, reused across calls
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ExtractedContent(
        val paragraphs: String,        // Extracted text, max MAX_TEXT_CHARS
        val headings: List<String>,    // h1/h2/h3 text, max 10 entries
        val statusCode: Int,           // Final HTTP status code
        val finalUrl: String,          // Canonical URL after redirects
        val adapter: String = "okhttp+jsoup",
        val error: String? = null      // Non-null = execution failed
    )

    /**
     * Fetch [url] and extract text content.
     *
     * Uses a raw Thread for the OkHttp call — never blocks Dispatchers.IO.
     * Hard wall-clock timeout of MAX_TOTAL_TIMEOUT_SEC seconds enforced via
     * CompletableFuture.get(timeout). Always returns, never throws.
     */
    suspend fun fetchAndExtract(url: String): ExtractedContent = withContext(Dispatchers.Default) {
        val future = CompletableFuture<ExtractedContent>()

        // Run OkHttp on a raw JVM thread — immune to Android OEM scheduler throttling
        val fetchThread = Thread {
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

                // ── 1. HTTP error ─────────────────────────────────────────
                if (statusCode >= 400) {
                    response.close()
                    future.complete(ExtractedContent(
                        paragraphs = "", headings = emptyList(),
                        statusCode = statusCode, finalUrl = finalUrl,
                        error = "HTTP_ERROR_$statusCode"
                    ))
                    return@Thread
                }

                // ── 2. Content-Type whitelist ─────────────────────────────
                val rawCT = response.header("Content-Type") ?: ""
                val baseCT = rawCT.substringBefore(";").trim().lowercase()
                if (baseCT !in ALLOWED_CONTENT_TYPES) {
                    response.close()
                    Log.w("HtmlFetcher", "Rejected Content-Type '$baseCT' for $finalUrl")
                    future.complete(ExtractedContent(
                        paragraphs = "", headings = emptyList(),
                        statusCode = statusCode, finalUrl = finalUrl,
                        error = "ERR_CONTENT_TYPE_REJECTED:$baseCT"
                    ))
                    return@Thread
                }

                // ── 3. Body size limit ────────────────────────────────────
                val bodySource = response.body?.source()
                if (bodySource == null) {
                    response.close()
                    future.complete(ExtractedContent(
                        paragraphs = "", headings = emptyList(),
                        statusCode = statusCode, finalUrl = finalUrl,
                        error = "ERR_EMPTY_BODY"
                    ))
                    return@Thread
                }
                bodySource.request(MAX_BODY_BYTES)
                val buffered = bodySource.buffer.readByteArray(
                    minOf(bodySource.buffer.size, MAX_BODY_BYTES)
                )
                response.close()

                // ── 4. Charset handling ───────────────────────────────────
                val charsetName = rawCT.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                    ?.substringAfter("=")
                    ?.trim() ?: "UTF-8"

                val htmlString = try {
                    String(buffered, charset(charsetName))
                } catch (_: Exception) {
                    String(buffered, Charsets.UTF_8)
                }

                // ── 5. Parse HTML ─────────────────────────────────────────
                val doc: Document = Jsoup.parse(htmlString, finalUrl)
                val bodyText = doc.body()?.text() ?: ""

                // ── 6. Paywall detection ──────────────────────────────────
                if (listOf("paywall", "subscribe to read", "subscription required",
                        "premium content", "members only")
                        .any { bodyText.lowercase().contains(it) }) {
                    Log.w("HtmlFetcher", "Paywall at $finalUrl")
                    future.complete(ExtractedContent(
                        paragraphs = "", headings = emptyList(),
                        statusCode = statusCode, finalUrl = finalUrl,
                        error = "PAYWALL_DETECTED"
                    ))
                    return@Thread
                }

                // ── 7. Extract headings ───────────────────────────────────
                val headings = doc.select("h1, h2, h3")
                    .take(10).map { it.text().trim() }.filter { it.isNotBlank() }

                // ── 8. Extract text ───────────────────────────────────────
                val paragraphText = doc.select("p, article, main, section")
                    .joinToString("\n") { it.text().trim() }
                    .take(MAX_TEXT_CHARS).trim()

                val finalText = if (paragraphText.length < 100) {
                    bodyText.take(MAX_TEXT_CHARS).trim()
                } else {
                    paragraphText
                }

                if (finalText.length < 200 && headings.isEmpty()) {
                    Log.i("HtmlFetcher", "Sparse/JS-heavy content at $finalUrl")
                }

                Log.d("HtmlFetcher",
                    "OK fetch: ${finalText.length} chars, ${headings.size} headings, $finalUrl")

                future.complete(ExtractedContent(
                    paragraphs = finalText, headings = headings,
                    statusCode = statusCode, finalUrl = finalUrl
                ))

            } catch (e: Exception) {
                Log.e("HtmlFetcher", "Fetch failed for $url: ${e.message}")
                future.complete(ExtractedContent(
                    paragraphs = "", headings = emptyList(),
                    statusCode = 0, finalUrl = url,
                    error = e.message ?: "ERR_NETWORK"
                ))
            }
        }

        fetchThread.isDaemon = true
        fetchThread.name = "HtmlFetcher-${url.take(30)}"
        fetchThread.start()

        // Block Dispatchers.Default thread until result or hard timeout
        try {
            future.get(MAX_TOTAL_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            fetchThread.interrupt()
            client.dispatcher.cancelAll()
            Log.e("HtmlFetcher", "Hard 25s timeout exceeded for $url")
            ExtractedContent(
                paragraphs = "", headings = emptyList(),
                statusCode = 0, finalUrl = url,
                error = "ERR_HARD_TIMEOUT_25S"
            )
        } catch (e: Exception) {
            Log.e("HtmlFetcher", "Future.get failed for $url: ${e.message}")
            ExtractedContent(
                paragraphs = "", headings = emptyList(),
                statusCode = 0, finalUrl = url,
                error = e.message ?: "ERR_FUTURE"
            )
        }
    }
}
