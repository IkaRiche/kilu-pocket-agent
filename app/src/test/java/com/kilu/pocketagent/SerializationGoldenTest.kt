package com.kilu.pocketagent

import com.kilu.pocketagent.shared.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Golden JSON + round-trip serialization tests.
 *
 * These tests catch:
 * - Fields dropped due to `encodeDefaults = false`
 * - Renamed or removed fields
 * - Broken nested objects / wrapper structures
 * - snake_case vs camelCase drift
 */
class SerializationGoldenTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // ── ApprovePlanReq ─────────────────────────────────────────────────────────

    @Test
    fun `ApprovePlanReq encodes all required fields including nested approval_receipt`() {
        val req = ApprovePlanReq(
            device_id = "dev_abc123",
            biometric_present = true,
            approval_receipt = ApprovalReceipt(
                pubkey_alg = "ED25519",
                pubkey_b64 = "base64pub==",
                signature_b64 = "base64sig=="
            )
        )
        val encoded = json.encodeToString(req)

        assertTrue(encoded.contains("\"device_id\":\"dev_abc123\""),
            "device_id missing from ApprovePlanReq JSON: $encoded")
        assertTrue(encoded.contains("\"biometric_present\":true"),
            "biometric_present missing (default dropped?): $encoded")
        assertTrue(encoded.contains("\"approval_receipt\""),
            "approval_receipt wrapper missing: $encoded")
        assertTrue(encoded.contains("\"pubkey_alg\":\"ED25519\""),
            "pubkey_alg missing inside approval_receipt: $encoded")
        assertTrue(encoded.contains("\"pubkey_b64\":\"base64pub==\""),
            "pubkey_b64 missing: $encoded")
        assertTrue(encoded.contains("\"signature_b64\":\"base64sig==\""),
            "signature_b64 missing: $encoded")
    }

    @Test
    fun `ApprovePlanReq biometric_present false is encoded`() {
        // Ensure false boolean is not dropped (it has no default so this should be fine,
        // but guard against future regression)
        val req = ApprovePlanReq(
            device_id = "dev_1",
            biometric_present = false,
            approval_receipt = ApprovalReceipt("ED25519", "pub", "sig")
        )
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains("\"biometric_present\":false"), "false value must not be dropped: $encoded")
    }

    // ── HubQueueListResponse ───────────────────────────────────────────────────

    @Test
    fun `HubQueueListResponse round-trip parses items wrapper correctly`() {
        val raw = """
            {
              "items": [
                {
                  "task_id": "tsk_001",
                  "grant_id": "gr_001",
                  "external_url": "https://example.com"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<HubQueueListResponse>(raw)
        assertEquals(1, parsed.items.size, "Expected 1 item in queue")
        assertEquals("tsk_001", parsed.items.first().task_id)
        assertEquals("gr_001", parsed.items.first().grant_id)

        // Round-trip: serialize again and verify wrapper still present
        val reEncoded = json.encodeToString(parsed)
        assertTrue(reEncoded.contains("\"items\""), "items wrapper lost in round-trip: $reEncoded")
        assertTrue(reEncoded.contains("tsk_001"), "task_id lost in round-trip: $reEncoded")
    }

    @Test
    fun `HubQueueListResponse empty items decodes correctly`() {
        val raw = """{"items":[]}"""
        val parsed = json.decodeFromString<HubQueueListResponse>(raw)
        assertEquals(0, parsed.items.size, "Expected empty list")
    }

    // ── MintStepBatchReq ───────────────────────────────────────────────────────

    @Test
    fun `MintStepBatchReq encodes all fields including steps`() {
        val req = MintStepBatchReq(
            runtime_id = "run_1",
            toolchain_id = "tc_1",
            steps = listOf(StepInfo("step_0", "digest"))
        )
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains("\"runtime_id\":\"run_1\""), "runtime_id missing: $encoded")
        assertTrue(encoded.contains("\"toolchain_id\":\"tc_1\""), "toolchain_id missing: $encoded")
        assertTrue(encoded.contains("\"steps\""), "steps array missing: $encoded")
        assertTrue(encoded.contains("\"step_id\":\"step_0\""), "step_id missing: $encoded")
    }

    // ── SubmitResultReq ────────────────────────────────────────────────────────

    @Test
    fun `SubmitResultReq encodes evidence correctly`() {
        val evidence = Evidence(
            task_id = "tsk_1",
            step_id = "step_0",
            runner_id = "run_1",
            adapter = "webview",
            outcome = "success",
            started_at = "2024-01-01T00:00:00Z",
            finished_at = "2024-01-01T00:00:01Z"
        )
        val req = SubmitResultReq(evidence = evidence)
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains("\"evidence\""), "evidence wrapper missing: $encoded")
        assertTrue(encoded.contains("\"outcome\":\"success\""), "outcome missing: $encoded")
        assertTrue(encoded.contains("\"started_at\":\"2024-01-01T00:00:00Z\""), "started_at missing: $encoded")
    }

    // ── CreateTaskReq ──────────────────────────────────────────────────────────

    @Test
    fun `CreateTaskReq encodes executor_preference`() {
        val req = CreateTaskReq(
            title = "Test Task",
            user_prompt = "Do something",
            executor_preference = "HUB_PREFERRED"
        )
        val encoded = json.encodeToString(req)
        assertTrue(encoded.contains("\"executor_preference\":\"HUB_PREFERRED\""),
            "executor_preference missing or dropped: $encoded")
        assertTrue(encoded.contains("\"title\":\"Test Task\""), "title missing: $encoded")
        assertTrue(encoded.contains("\"user_prompt\":\"Do something\""), "user_prompt missing: $encoded")
    }
}
