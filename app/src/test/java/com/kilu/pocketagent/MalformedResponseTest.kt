package com.kilu.pocketagent

import com.kilu.pocketagent.core.network.ControlPlaneApi
import com.kilu.pocketagent.core.utils.PrintLogger
import com.kilu.pocketagent.shared.models.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Negative / malformed response tests.
 *
 * Verifies that the client:
 * - Never crashes on bad server responses
 * - Always logs the error (observable via test completion, not crash)
 * - Returns the correct fail-closed result (emptyList / null / false)
 * - Does NOT silently swallow errors and pretend success
 *
 * This is critical for a security-oriented product.
 */
class MalformedResponseTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ControlPlaneApi

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        api = ControlPlaneApi(
            client = OkHttpClient(),
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            logger = PrintLogger
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── pollQueue malformed responses ──────────────────────────────────────────

    @Test
    fun `pollQueue does not crash on HTML error page response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("<html><body>Service Unavailable</body></html>")
        )

        val result = api.pollQueue()
        assertEquals(0, result.size, "Must return emptyList on HTML error page, not throw")
    }

    @Test
    fun `pollQueue does not crash on empty body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = api.pollQueue()
        assertEquals(0, result.size, "Must return emptyList on empty body")
    }

    @Test
    fun `pollQueue does not crash on items null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":null}"""))

        // Should not crash — items defaults to emptyList in HubQueueListResponse
        val result = api.pollQueue()
        assertEquals(0, result.size, "items:null must return emptyList")
    }

    @Test
    fun `pollQueue does not crash on completely wrong JSON shape`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"error":"not_found","code":404}""")
        )

        val result = api.pollQueue()
        assertEquals(0, result.size, "Wrong JSON shape must return emptyList, not throw")
    }

    @Test
    fun `pollQueue does not crash on string value for items field`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"items":"NONE"}""")
        )

        val result = api.pollQueue()
        assertEquals(0, result.size, "Wrong type for items field must not throw")
    }

    // ── approvePlan malformed responses ────────────────────────────────────────

    @Test
    fun `approvePlan does not crash on HTML error page`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("<html>Error</html>")
        )

        val result = api.approvePlan("pln_1", ApprovePlanReq(
            device_id = "dev_1",
            biometric_present = true,
            approval_receipt = ApprovalReceipt("ED25519", "pub", "sig")
        ))

        // Should return null, not throw
        assertEquals(null, result, "HTML body should result in null, not crash")
    }

    @Test
    fun `approvePlan does not crash on empty response body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = api.approvePlan("pln_1", ApprovePlanReq(
            device_id = "dev_1",
            biometric_present = true,
            approval_receipt = ApprovalReceipt("ED25519", "pub", "sig")
        ))

        assertEquals(null, result)
    }

    // ── submitResult fail-closed ───────────────────────────────────────────────

    @Test
    fun `submitResult returns false on 500 - does not pretend success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val fakeHashes = kotlinx.serialization.json.buildJsonObject { }
        val req = SubmitResultReq(
            url = "https://example.com",
            extracted_text = "some text",
            summary = "summary",
            hashes = fakeHashes
        )

        val result = api.submitResult("tsk_1", req)
        assertFalse(result, "submitResult must return false on 500, not true")
    }

    @Test
    fun `submitResult returns false on 400`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"ERR_BAD_REQUEST"}"""))

        val fakeHashes = kotlinx.serialization.json.buildJsonObject { }
        val req = SubmitResultReq(
            url = "https://example.com",
            extracted_text = "text",
            summary = "summary",
            hashes = fakeHashes
        )

        val result = api.submitResult("tsk_1", req)
        assertFalse(result, "submitResult must return false on 400")
    }
}
