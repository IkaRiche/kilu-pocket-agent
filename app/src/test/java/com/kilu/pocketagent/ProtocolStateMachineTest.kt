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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Protocol State Machine Tests.
 *
 * Verifies how the Android client (ControlPlaneApi) interacts with state-dependent endpoints
 * and correctly handles server-side state rejections (400, 401, 403, 409, 422).
 * 
 * Also verifies exact path matching for consistency.
 */
class ProtocolStateMachineTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ControlPlaneApi
    private var tokenClearedCount = 0

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        api = ControlPlaneApi(
            client = OkHttpClient(),
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            logger = PrintLogger,
            onAuthFailure = { tokenClearedCount++ }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mockApproveReq() = ApprovePlanReq(
        device_id = "test_dev",
        biometric_present = true,
        approval_receipt = ApprovalReceipt("ED25519", "pub", "sig")
    )

    // ── Transition: Approve without Plan ───────────────────────────────────────

    @Test
    fun `approve without a valid plan yields null and logs fail-closed`() = runTest {
        // Server rejects approval because the plan ID is invalid or not in AWAITING_APPROVAL state
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"plan_not_found"}"""))
        
        val result = api.approvePlan("invalid_plan", mockApproveReq())
        assertNull(result, "Approving an invalid plan must return null (fail-closed)")
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/plans/invalid_plan/approve"), "Path consistency check failed")
    }

    // ── Transition: Mint without valid grant ───────────────────────────────────

    @Test
    fun `mint step batch without valid grant yields null`() = runTest {
        // Server rejects because grant is expired or already used up (400 or 422)
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"grant_exhausted"}"""))

        val req = MintStepBatchReq(
            runtime_id = "dev_1",
            toolchain_id = "tc_1",
            steps = listOf(StepInfo("step_0", "digest"))
        )
        val result = api.mintStepBatch("exhausted_grant", req)
        assertNull(result, "Minting on invalid grant must return null (fail-closed)")
        val serverReq = server.takeRequest()
        assertTrue(serverReq.path!!.endsWith("/grants/exhausted_grant/mint-step-batch"), "Path consistency check failed")
    }

    // ── Transition: Submit result twice ────────────────────────────────────────

    @Test
    fun `submit result twice handles idempotent 409 properly`() = runTest {
        // Server returns HTTP 409 Conflict when a result is submitted for an already DONE task.
        // Contract states: 409 is treated as a success from the client orchestrator perspective (idempotent).
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"task_already_done"}"""))

        val evidence = Evidence(
            task_id = "tsk_1",
            step_id = "step_0",
            runner_id = "dev_1",
            adapter = "webview",
            outcome = "success",
            started_at = "2024-01-01T00:00:00Z",
            finished_at = "2024-01-01T00:00:01Z",
            stdout_hash = "sha256:abc",
            exit_code = 0
        )
        val reqObj = SubmitResultReq(evidence = evidence)
        val result = api.submitResult("tsk_already_done", reqObj)
        
        assertTrue(result, "HTTP 409 must be treated as success in submitResult because it means task is already DONE")
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/tasks/tsk_already_done/result"), "Path consistency check failed")
    }

    // ── Auth Clearing Consistency ──────────────────────────────────────────────

    @Test
    fun `401 and 403 clear session consistently across ALL endpoints`() = runTest {
        // Test pollQueue
        server.enqueue(MockResponse().setResponseCode(401))
        api.pollQueue()
        
        // Test approvePlan
        server.enqueue(MockResponse().setResponseCode(403))
        api.approvePlan("pln_abc", mockApproveReq())
        
        // Test mintStepBatch
        server.enqueue(MockResponse().setResponseCode(401))
        val mintReq = MintStepBatchReq(
            runtime_id = "dev_1",
            toolchain_id = "tc_1",
            steps = listOf(StepInfo("step_0", "digest"))
        )
        api.mintStepBatch("gr_123", mintReq)
        
        // Test submitResult
        server.enqueue(MockResponse().setResponseCode(403))
        val evidence = Evidence(
            task_id = "tsk_1",
            step_id = "step_0",
            runner_id = "dev_1",
            adapter = "webview",
            outcome = "success",
            started_at = "2024-01-01T00:00:00Z",
            finished_at = "2024-01-01T00:00:01Z",
            stdout_hash = "sha256:abc",
            exit_code = 0
        )
        api.submitResult("tsk_abc", SubmitResultReq(evidence = evidence))

        assertEquals(4, tokenClearedCount, "onAuthFailure must be called exactly 4 times (once per endpoint auth error)")
    }

    // ── State: Queue behavior ──────────────────────────────────────────────────

    @Test
    fun `poll queue after lease shows empty items array instead of bare block`() = runTest {
        // When queue is empty (all leased), server sends { items: [] }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        
        val result = api.pollQueue(1)
        assertEquals(0, result.size, "Must gracefully handle empty wrapper array")
    }

    // ── Broken semantic payload (Fail closed on 200) ───────────────────────────

    @Test
    fun `server returns 200 with semantically broken payload fails closed`() = runTest {
        // 200 OK, but JSON is missing grant_id for approve endpoint
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"error_actually"}"""))
        
        val result = api.approvePlan("pln_abc", mockApproveReq())
        assertNull(result, "Missing required fields on a 200 response must throw a parser error caught and return null")
    }
}
