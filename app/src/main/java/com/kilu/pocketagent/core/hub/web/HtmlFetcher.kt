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
 * HtmlFetcher — replaces WebView for background service content extraction.
 *
 * Uses OkHttp (pure HTTP) + Jsoup (HTML parsing) — no WebView, no UI thread,
 * no Main dispatcher dependency. Reliable in Android foreground services.
 *
 * OkHttp has built-in connect/read/write timeouts — guaranteed to complete.
 */
class HtmlFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class FetchResult(
        val html: String,
        val finalUrl: String,
        val statusCode: Int
    )

    data class ExtractedContent(
        val paragraphs: String,
        val headings: List<String>,
        val statusCode: Int,
        val error: String? = null
    )

    /**
     * Fetch URL and extract text content.
     * Runs on Dispatchers.IO — completely thread-safe, no UI dependency.
     */
    suspend fun fetchAndExtract(url: String): ExtractedContent = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()

            val response = client.newCall(request).execute()
            val statusCode = response.code
            val body = response.body?.string() ?: ""
            response.close()

            if (statusCode >= 400) {
                Log.w("HtmlFetcher", "HTTP $statusCode for $url")
                return@withContext ExtractedContent(
                    paragraphs = "",
                    headings = emptyList(),
                    statusCode = statusCode,
                    error = "HTTP Error $statusCode"
                )
            }

            val doc: Document = Jsoup.parse(body, url)

            // Extract headings
            val headings = doc.select("h1, h2, h3")
                .take(10)
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            // Extract paragraph text
            val paragraphText = doc.select("p, article, main, section")
                .joinToString("\n") { it.text().trim() }
                .take(4000)
                .trim()

            // Fallback: use body text if no paragraphs
            val finalText = if (paragraphText.length < 100) {
                doc.body()?.text()?.take(4000)?.trim() ?: ""
            } else {
                paragraphText
            }

            // Check for paywall/subscription blocks
            val bodyText = doc.body()?.text()?.lowercase() ?: ""
            val isPaywalled = listOf("paywall", "subscribe to read", "subscription required")
                .any { bodyText.contains(it) }

            if (isPaywalled) {
                Log.w("HtmlFetcher", "Paywall detected at $url")
                return@withContext ExtractedContent(
                    paragraphs = finalText,
                    headings = headings,
                    statusCode = statusCode,
                    error = "PAYWALL_DETECTED"
                )
            }

            Log.d("HtmlFetcher", "Extracted ${finalText.length} chars, ${headings.size} headings from $url")
            ExtractedContent(
                paragraphs = finalText,
                headings = headings,
                statusCode = statusCode
            )
        } catch (e: Exception) {
            Log.e("HtmlFetcher", "Fetch failed for $url: ${e.message}")
            ExtractedContent(
                paragraphs = "",
                headings = emptyList(),
                statusCode = 0,
                error = e.message ?: "Network error"
            )
        }
    }
}
