package com.kilu.pocketagent

import com.kilu.pocketagent.core.network.ControlPlaneApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for approvePlan endpoint.
 *
 * Verifies that ControlPlaneApi.approvePlan() sends the correct HTTP request body:
 * - device_id present and non-empty
 * - biometric_present = true (NOT dropped as a default)
 * - approval_receipt is a nested object
 * - pubkey_alg = ED25519 present inside receipt
 *
 * Also tests error responses.
 */
class ApprovePlanContractTest {

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
            onAuthFailure = { authFailureCalled = true }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun makeReq(deviceId: String = "dev_test") = ApprovePlanReq(
        device_id = deviceId,
        biometric_present = true,
        approval_receipt = ApprovalReceipt(
            pubkey_alg = "ED25519",
            pubkey_b64 = "base64pub==",
            signature_b64 = "base64sig=="
        )
    )

    // ── Request body inspection ────────────────────────────────────────────────

    @Test
    fun `approvePlan sends correct HTTP method and path`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"grant_id":"gr_1","status":"APPROVED"}""")
        )

        api.approvePlan("pln_abc", makeReq())

        val req = server.takeRequest()
        assertEquals("POST", req.method, "Must be POST")
        assertTrue(req.path!!.endsWith("/plans/pln_abc/approve"),
            "Wrong path: ${req.path}")
        assertEquals("application/json", req.getHeader("Content-Type")?.split(";")?.first()?.trim())
    }

    @Test
    fun `approvePlan request body contains all required fields`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"grant_id":"gr_1","status":"APPROVED"}""")
        )

        api.approvePlan("pln_abc", makeReq("dev_real"))

        val body = server.takeRequest().body.readUtf8()

        assertTrue(body.contains("\"device_id\":\"dev_real\""),
            "device_id missing from request body: $body")
        assertTrue(body.contains("\"biometric_present\":true"),
            "biometric_present missing — possible encodeDefaults issue: $body")
        assertTrue(body.contains("\"approval_receipt\""),
            "approval_receipt nested object missing: $body")
        assertTrue(body.contains("\"pubkey_alg\":\"ED25519\""),
            "pubkey_alg missing inside approval_receipt: $body")
        assertTrue(body.contains("\"pubkey_b64\":\"base64pub==\""),
            "pubkey_b64 missing: $body")
        assertTrue(body.contains("\"signature_b64\":\"base64sig==\""),
            "signature_b64 missing: $body")
    }

    @Test
    fun `approvePlan returns grant on 200`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"grant_id":"gr_success","status":"APPROVED"}""")
        )

        val result = api.approvePlan("pln_1", makeReq())

        assertNotNull(result, "Should return ApprovePlanResp on 200")
        assertEquals("gr_success", result.grant_id)
        assertEquals("APPROVED", result.status)
    }

    // ── Error responses ────────────────────────────────────────────────────────

    @Test
    fun `approvePlan returns null on 400 schema validation error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"code":"ERR_SCHEMA_VALIDATION","message":"Payload failed schema validation"}""")
        )

        val result = api.approvePlan("pln_1", makeReq())
        assertNull(result, "Should return null on 400 (not throw)")
    }

    @Test
    fun `approvePlan triggers auth failure on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = api.approvePlan("pln_1", makeReq())
        assertNull(result)
        assertTrue(authFailureCalled, "onAuthFailure must be called on 401")
    }

    @Test
    fun `approvePlan returns null on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Error"))

        val result = api.approvePlan("pln_1", makeReq())
        assertNull(result, "Must fail-closed and return null on 500")
    }
}
