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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract tests for hub/queue endpoint.
 *
 * Verifies that ControlPlaneApi.pollQueue() correctly:
 * - Parses the { "items": [...] } wrapper (regression test for the silent emptyList bug)
 * - Returns proper list on success
 * - Clears auth on 401/403
 * - Logs and returns emptyList on parse errors / server errors
 */
class HubQueueContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ControlPlaneApi
    private var authFailureCalled = false

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        api = ControlPlaneApi(
            client = OkHttpClient(),
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            logger = PrintLogger,
            onAuthFailure = { authFailureCalled = true }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    fun `pollQueue returns items from wrapper response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "items": [
                        {
                          "task_id": "tsk_abc",
                          "grant_id": "gr_xyz",
                          "external_url": "https://example.com/page"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val result = api.pollQueue()

        assertEquals(1, result.size, "Should return 1 item from wrapper. Got: $result")
        assertEquals("tsk_abc", result.first().task_id)
        assertEquals("gr_xyz", result.first().grant_id)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("hub/queue"), "Wrong path: ${request.path}")
    }

    @Test
    fun `pollQueue returns empty list when items array is empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))

        val result = api.pollQueue()
        assertEquals(0, result.size)
    }

    // ── This is the REGRESSION test for the original bug ─────────────────────

    @Test
    fun `pollQueue does NOT return emptyList when server sends wrapper`() = runTest {
        // Server correctly sends { items: [...] }
        // Old code parsed as List<HubQueueResponse> and would silently return []
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"task_id":"tsk_regression","grant_id":"gr_1"}]}"""
            )
        )

        val result = api.pollQueue()

        assertTrue(result.isNotEmpty(),
            "REGRESSION: pollQueue returned emptyList() when server sent {items:[...]} wrapper")
        assertEquals("tsk_regression", result.first().task_id)
    }

    // ── Auth errors ────────────────────────────────────────────────────────────

    @Test
    fun `pollQueue returns emptyList and triggers auth failure on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = api.pollQueue()
        assertEquals(0, result.size)
        assertTrue(authFailureCalled, "onAuthFailure callback not triggered on 401")
    }

    @Test
    fun `pollQueue returns emptyList and triggers auth failure on 403`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = api.pollQueue()
        assertEquals(0, result.size)
        assertTrue(authFailureCalled, "onAuthFailure callback not triggered on 403")
    }

    // ── Server errors ──────────────────────────────────────────────────────────

    @Test
    fun `pollQueue returns emptyList on 500 server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Error"))

        val result = api.pollQueue()
        assertEquals(0, result.size, "Must return emptyList on server error, not throw")
    }

    @Test
    fun `pollQueue returns emptyList on malformed JSON (bare array)`() = runTest {
        // Simulate old server behavior returning bare array instead of wrapper
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"task_id":"tsk_1","grant_id":"gr_1"}]"""
            )
        )

        // Should not throw — fail-closed: return emptyList and log error
        val result = api.pollQueue()
        assertEquals(0, result.size, "Malformed response must return emptyList, not throw")
    }
}
