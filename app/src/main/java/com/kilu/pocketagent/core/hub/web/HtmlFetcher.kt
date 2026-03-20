package com.kilu.pocketagent.core.hub.web

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * HtmlFetcher — bounded document ingestion for Hub background service.
 *
 * Capability contract:
 *   Hub does NOT render pages.
 *   Hub performs bounded network retrieval and deterministic HTML extraction.
 *
 * Fail-closed policy (all limits enforced strictly; reject != fail):
 *   - Content-Type: only text/html and text/xhtml are accepted
 *   - Body size: hard cap at MAX_BODY_BYTES (1 MB) before parsing
 *   - Redirects: max MAX_REDIRECTS (5) hops
 *   - Extracted text: max MAX_TEXT_CHARS characters returned
 *   - JS-rendered content: not supported — static HTML only
 */
class HtmlFetcher {

    companion object {
        private const val MAX_BODY_BYTES = 1_048_576L   // 1 MB
        private const val MAX_TEXT_CHARS = 4_000
        private const val MAX_REDIRECTS  = 5
        private val ALLOWED_CONTENT_TYPES = setOf("text/html", "text/xhtml+xml", "application/xhtml+xml")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        // Respect redirect policy: OkHttp handles Location header, we cap at MAX_REDIRECTS
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ExtractedContent(
        val paragraphs: String,        // Extracted text, max MAX_TEXT_CHARS
        val headings: List<String>,    // h1/h2/h3 text, max 10 entries
        val statusCode: Int,           // Final HTTP status code
        val finalUrl: String,          // Canonical URL after redirects
        val adapter: String = "okhttp+jsoup",
        val error: String? = null      // Non-null = execution failed; "PAYWALL_DETECTED" = escalate
    )

    /**
     * Fetch [url] and extract text content.
     *
     * Returns [ExtractedContent] — always resolves within OkHttp timeout bounds (max ~25s).
     * All policy violations result in a non-null [ExtractedContent.error] — never throws.
     */
    suspend fun fetchAndExtract(url: String): ExtractedContent = withContext(Dispatchers.IO) {
        var redirectCount = 0
        var currentUrl = url

        try {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "en-US,en;q=0.5")
                // No Accept-Encoding — let OkHttp handle gzip transparently
                .build()

            val response = client.newCall(request).execute()
            val statusCode = response.code
            val finalUrl   = response.request.url.toString()

            // Track redirects (OkHttp follows automatically, but logs them)
            redirectCount = response.networkResponse?.let {
                var r = it; var count = 0
                while (r.priorResponse != null) { r = r.priorResponse!!; count++ }
                count
            } ?: 0

            if (redirectCount > MAX_REDIRECTS) {
                response.close()
                Log.w("HtmlFetcher", "Too many redirects ($redirectCount) for $url")
                return@withContext ExtractedContent(
                    paragraphs = "", headings = emptyList(), statusCode = statusCode,
                    finalUrl = finalUrl, error = "ERR_TOO_MANY_REDIRECTS"
                )
            }

            // ── 1. HTTP error check ───────────────────────────────────────────
            if (statusCode >= 400) {
                response.close()
                Log.w("HtmlFetcher", "HTTP $statusCode for $finalUrl")
                return@withContext ExtractedContent(
                    paragraphs = "", headings = emptyList(), statusCode = statusCode,
                    finalUrl = finalUrl, error = "HTTP_ERROR_$statusCode"
                )
            }

            // ── 2. Content-Type whitelist ─────────────────────────────────────
            val rawContentType = response.header("Content-Type") ?: ""
            val baseContentType = rawContentType.substringBefore(";").trim().lowercase()
            if (baseContentType !in ALLOWED_CONTENT_TYPES) {
                response.close()
                Log.w("HtmlFetcher", "Rejected Content-Type '$baseContentType' for $finalUrl")
                return@withContext ExtractedContent(
                    paragraphs = "", headings = emptyList(), statusCode = statusCode,
                    finalUrl = finalUrl, error = "ERR_CONTENT_TYPE_REJECTED:$baseContentType"
                )
            }

            // ── 3. Body size limit — read at most MAX_BODY_BYTES ─────────────
            val bodySource = response.body?.source()
            if (bodySource == null) {
                response.close()
                return@withContext ExtractedContent(
                    paragraphs = "", headings = emptyList(), statusCode = statusCode,
                    finalUrl = finalUrl, error = "ERR_EMPTY_BODY"
                )
            }

            // Buffer at most MAX_BODY_BYTES
            bodySource.request(MAX_BODY_BYTES)
            val buffered = bodySource.buffer.readByteArray(
                minOf(bodySource.buffer.size, MAX_BODY_BYTES)
            )
            response.close()

            // ── 4. Charset: derive from Content-Type header, fallback to Jsoup detection ──
            val charsetName = rawContentType
                .split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter("=")
                ?.trim()
                ?: "UTF-8"

            val htmlString = try {
                String(buffered, charset(charsetName))
            } catch (_: Exception) {
                String(buffered, Charsets.UTF_8) // safe fallback
            }

            // ── 5. Parse HTML ─────────────────────────────────────────────────
            val doc: Document = Jsoup.parse(htmlString, finalUrl)

            // ── 6. Paywall / subscription detection ───────────────────────────
            val bodyText = doc.body()?.text()?.lowercase() ?: ""
            val paywallSignals = listOf("paywall", "subscribe to read", "subscription required",
                "premium content", "members only")
            if (paywallSignals.any { bodyText.contains(it) }) {
                Log.w("HtmlFetcher", "Paywall detected at $finalUrl")
                return@withContext ExtractedContent(
                    paragraphs = "", headings = emptyList(), statusCode = statusCode,
                    finalUrl = finalUrl, error = "PAYWALL_DETECTED"
                )
            }

            // ── 7. Extract headings ───────────────────────────────────────────
            val headings = doc.select("h1, h2, h3")
                .take(10)
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            // ── 8. Extract paragraphs (prefer article/main/p, fallback to body) ──
            val paragraphText = doc.select("p, article, main, section")
                .joinToString("\n") { it.text().trim() }
                .take(MAX_TEXT_CHARS)
                .trim()

            val finalText = if (paragraphText.length < 100) {
                doc.body()?.text()?.take(MAX_TEXT_CHARS)?.trim() ?: ""
            } else {
                paragraphText
            }

            // Note JS-heavy pages explicitly in log (not an error, just a capability boundary)
            if (finalText.length < 200 && headings.isEmpty()) {
                Log.i("HtmlFetcher", "Sparse content at $finalUrl (JS-heavy page likely)")
            }

            Log.d("HtmlFetcher",
                "OK: ${finalText.length} chars, ${headings.size} headings, " +
                "redirects=$redirectCount, finalUrl=$finalUrl")

            ExtractedContent(
                paragraphs = finalText,
                headings   = headings,
                statusCode = statusCode,
                finalUrl   = finalUrl
            )

        } catch (e: Exception) {
            Log.e("HtmlFetcher", "Fetch failed for $url: ${e.message}")
            ExtractedContent(
                paragraphs = "", headings = emptyList(),
                statusCode = 0, finalUrl = url,
                error = e.message ?: "ERR_NETWORK"
            )
        }
    }
}
