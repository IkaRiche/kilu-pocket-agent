package com.kilu.pocketagent.core.network

import com.kilu.pocketagent.core.utils.AppLogger
import com.kilu.pocketagent.core.utils.AndroidLogger
import com.kilu.pocketagent.features.approver.ResolveAssumptionsReq
import com.kilu.pocketagent.shared.models.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Single HTTP + JSON boundary for all KiLu Control Plane calls.
 *
 * This is the ONLY class that knows about:
 *  - Exact API paths
 *  - Request/response JSON shapes
 *  - Auth token clearing on 401/403
 *
 * HubRuntimeLoop, PlanPreviewScreen etc. must delegate to this class
 * and NEVER build HTTP requests themselves.
 *
 * Testable via MockWebServer by passing a custom `baseUrl`.
 */
class ControlPlaneApi(
    val client: OkHttpClient,
    val baseUrl: String,
    private val logger: AppLogger = AndroidLogger,
    private val onAuthFailure: (() -> Unit)? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false   // null String? fields must be omitted, not sent as JSON null
        // Server schemas use type:"string" for optional fields — null is rejected by Ajv
    }

    private val mediaTypeJson = "application/json".toMediaType()

    // ── Hub Queue ──────────────────────────────────────────────────────────────

    // Uses enqueue() + suspendCancellableCoroutine for non-blocking dispatch,
    // then reads/closes body on Dispatchers.IO to avoid NetworkOnMainThreadException.
    suspend fun pollQueue(max: Int = 1): List<HubQueueResponse> {
        return try {
            val req = Request.Builder()
                .url("$baseUrl/hub/queue?max=$max")
                .get()
                .build()
            val resp = client.newCall(req).awaitResponse()
            withContext(Dispatchers.IO) {
                resp.use { r ->
                    when {
                        r.isSuccessful -> {
                            val body = r.body?.string() ?: ""
                            val wrapper = json.decodeFromString<HubQueueListResponse>(body)
                            logger.d("ControlPlaneApi", "pollQueue ok items=${wrapper.items.size}")
                            wrapper.items
                        }
                        r.code == 401 || r.code == 403 -> {
                            logger.e("ControlPlaneApi", "pollQueue auth error ${r.code}")
                            onAuthFailure?.invoke()
                            emptyList()
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "pollQueue http=${r.code}")
                            emptyList()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e("ControlPlaneApi", "pollQueue parse/fetch error", e)
            emptyList()
        }
    }

    // ── Plan Approval ──────────────────────────────────────────────────────────

    /**
     * Approve a plan. Returns Result:
     *   - success: ApprovePlanResp (grant created)
     *   - failure: exception message contains server error code/message for display
     */
    suspend fun approvePlan(planId: String, req: ApprovePlanReq): Result<ApprovePlanResp> =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(req).toByteArray().toRequestBody(mediaTypeJson)
                val httpReq = Request.Builder()
                    .url("$baseUrl/plans/$planId/approve")
                    .post(body)
                    .build()
                client.newCall(httpReq).execute().use { resp ->
                    val respBodyStr = resp.body?.string() ?: ""
                    when {
                        resp.isSuccessful -> {
                            Result.success(json.decodeFromString<ApprovePlanResp>(respBodyStr))
                        }
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "approvePlan auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            val serverCode = try {
                                val obj = json.parseToJsonElement(respBodyStr)
                                val err = obj.jsonObject["error"]?.jsonObject
                                val code = err?.get("code")?.jsonPrimitive?.content ?: "ERR_AUTH"
                                val msg = err?.get("message")?.jsonPrimitive?.content ?: "Auth error"
                                "[$code] $msg"
                            } catch (_: Exception) { "[${resp.code}] Auth error" }
                            Result.failure(Exception(serverCode))
                        }
                        else -> {
                            val serverError = try {
                                val obj = json.parseToJsonElement(respBodyStr)
                                val err = obj.jsonObject["error"]?.jsonObject
                                val code = err?.get("code")?.jsonPrimitive?.content ?: "ERR_UNKNOWN"
                                val msg = err?.get("message")?.jsonPrimitive?.content ?: respBodyStr.take(200)
                                "[$code] $msg"
                            } catch (_: Exception) { "[HTTP ${resp.code}] $respBodyStr".take(200) }
                            logger.e("ControlPlaneApi", "approvePlan failed: $serverError")
                            Result.failure(Exception(serverError))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "approvePlan error", e)
                Result.failure(e)
            }
        }

    // ── Mint Step Batch ────────────────────────────────────────────────────────

    /**
     * Returns true on success, false on quota/error.
     * Throws on unrecoverable 401/403 (token cleared).
     */
    suspend fun mintStepBatch(grantId: String, req: MintStepBatchReq): MintStepBatchResp? {
        return try {
            val body = json.encodeToString(req).toByteArray()
                .toRequestBody(mediaTypeJson)
            val httpReq = Request.Builder()
                .url("$baseUrl/grants/$grantId/mint-step-batch")
                .post(body)
                .build()
            val resp = client.newCall(httpReq).awaitResponse()
            withContext(Dispatchers.IO) {
                resp.use { r ->
                    when {
                        r.isSuccessful -> {
                            val respBody = r.body?.string() ?: ""
                            json.decodeFromString<MintStepBatchResp>(respBody)
                        }
                        r.code == 401 || r.code == 403 -> {
                            logger.e("ControlPlaneApi", "mintStepBatch auth error ${r.code}")
                            onAuthFailure?.invoke()
                            null
                        }
                        r.code == 429 -> {
                            logger.e("ControlPlaneApi", "mintStepBatch quota exceeded")
                            null
                        }
                        else -> {
                            val errBody = r.body?.string() ?: "(empty)"
                            logger.e("ControlPlaneApi", "mintStepBatch http=${r.code} body=$errBody")
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e("ControlPlaneApi", "mintStepBatch error", e)
            null
        }
    }

    // ── Submit Result ──────────────────────────────────────────────────────────

    /**
     * Returns true on success or idempotent (409), false on any error.
     */
    suspend fun submitResult(taskId: String, req: SubmitResultReq): Boolean {
        return try {
            val bodyStr = json.encodeToString(req)
            logger.d("ControlPlaneApi", "submitResult body=$bodyStr")
            val body = bodyStr.toByteArray().toRequestBody(mediaTypeJson)
            val httpReq = Request.Builder()
                .url("$baseUrl/tasks/$taskId/result")
                .post(body)
                .build()
            val resp = client.newCall(httpReq).awaitResponse()
            withContext(Dispatchers.IO) {
                resp.use { r ->
                    logger.d("ControlPlaneApi", "submitResult HTTP=${r.code} task=$taskId")
                    when {
                        r.isSuccessful || r.code == 409 -> true
                        r.code == 401 || r.code == 403 -> {
                            logger.e("ControlPlaneApi", "submitResult auth error ${r.code}")
                            onAuthFailure?.invoke()
                            false
                        }
                        else -> {
                            val errBody = r.body?.string() ?: "(empty)"
                            logger.e("ControlPlaneApi", "submitResult http=${r.code} body=$errBody")
                            false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e("ControlPlaneApi", "submitResult error", e)
            false
        }
    }
    // ── Lease Refresh ──────────────────────────────────────────────────────────

    suspend fun refreshLease(taskId: String): Boolean {
        return try {
            val body = "{\"task_id\":\"$taskId\"}".toRequestBody(mediaTypeJson)
            val httpReq = Request.Builder()
                .url("$baseUrl/hub/lease/refresh")
                .post(body)
                .build()
            val resp = client.newCall(httpReq).awaitResponse()
            withContext(Dispatchers.IO) {
                resp.use { r ->
                    when {
                        r.isSuccessful -> true
                        r.code == 401 || r.code == 403 -> {
                            logger.e("ControlPlaneApi", "refreshLease auth error ${r.code}")
                            onAuthFailure?.invoke()
                            false
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "refreshLease http=${r.code}")
                            false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e("ControlPlaneApi", "refreshLease error", e)
            false
        }
    }

    // ── Escalation / Assumptions ───────────────────────────────────────────────

    suspend fun requestAssumptions(taskId: String, req: RequestAssumptionsReq): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(req).toByteArray().toRequestBody(mediaTypeJson)
                val httpReq = Request.Builder()
                    .url("$baseUrl/tasks/$taskId/assumptions/request")
                    .post(body)
                    .build()
                client.newCall(httpReq).execute().use { resp ->
                    when {
                        resp.isSuccessful -> true
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "requestAssumptions auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            false
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "requestAssumptions http=${resp.code}")
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "requestAssumptions error", e)
                false
            }
        }

    // ── Approver UI Endpoints ──────────────────────────────────────────────────

    suspend fun getPlan(taskId: String): PlanPreviewResp? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/tasks/$taskId/plan")
                    .post("{\"planner_mode\": \"managed\"}".toRequestBody(mediaTypeJson))
                    .build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val body = resp.body?.string() ?: ""
                            json.decodeFromString<PlanPreviewResp>(body)
                        }
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "getPlan auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            null
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "getPlan http=${resp.code}")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "getPlan error", e)
                null
            }
        }

    suspend fun createTask(req: CreateTaskReq): CreateTaskResp? =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(req).toByteArray().toRequestBody(mediaTypeJson)
                val httpReq = Request.Builder()
                    .url("$baseUrl/tasks")
                    .post(body)
                    .build()
                client.newCall(httpReq).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val respBody = resp.body?.string() ?: ""
                            json.decodeFromString<CreateTaskResp>(respBody)
                        }
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "createTask auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            null
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "createTask http=${resp.code}")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "createTask error", e)
                null
            }
        }

    suspend fun getQuotas(): QuotasResp? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$baseUrl/quotas").get().build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> json.decodeFromString<QuotasResp>(resp.body?.string() ?: "")
                        resp.code == 401 || resp.code == 403 -> { onAuthFailure?.invoke(); null }
                        else -> null
                    }
                }
            } catch (e: Exception) { null }
        }

    suspend fun getTasks(limit: Int = 20): List<ApproverTaskItem>? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$baseUrl/tasks?limit=$limit").get().build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> json.decodeFromString<List<ApproverTaskItem>>(resp.body?.string() ?: "[]")
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "getTasks auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            null
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "getTasks http=${resp.code}")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "getTasks error", e)
                null
            }
        }

    suspend fun getTask(taskId: String): ApproverTaskItem? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$baseUrl/tasks/$taskId").get().build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val body = resp.body?.string() ?: ""
                            json.decodeFromString<ApproverTaskItem>(body)
                        }
                        else -> null
                    }
                }
            } catch (e: Exception) { null }
        }

    /** Full task detail — returns TaskDetail with result, failure, runtime info. */
    suspend fun getTaskDetail(taskId: String): TaskDetail? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$baseUrl/tasks/$taskId").get().build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val body = resp.body?.string() ?: ""
                            json.decodeFromString<TaskDetail>(body)
                        }
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "getTaskDetail auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            null
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "getTaskDetail http=${resp.code}")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "getTaskDetail error", e)
                null
            }
        }


    /**
     * Hub heartbeat — keeps hub_runtimes.status=ONLINE and last_seen_at fresh.
     * Called once at loop startup and every 5 min thereafter.
     * Returns false on auth failure (caller should handle session expiry).
     */
    suspend fun registerRuntime(runtimeId: String, toolchainId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"runtime_id":"$runtimeId","toolchain_id":"$toolchainId"}"""
                    .toRequestBody(mediaTypeJson)
                val req = Request.Builder()
                    .url("$baseUrl/runtimes/register")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> true
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "registerRuntime auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            false
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "registerRuntime http=${resp.code}")
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "registerRuntime error", e)
                false
            }
        }


    suspend fun cancelTask(taskId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = "{\"reason\":\"Cancelled by user\"}".toRequestBody(mediaTypeJson)
                val req = Request.Builder().url("$baseUrl/tasks/$taskId/cancel").post(body).build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> true
                        resp.code == 401 || resp.code == 403 -> { onAuthFailure?.invoke(); false }
                        else -> false
                    }
                }
            } catch (e: Exception) { false }
        }

    suspend fun getInbox(max: Int = 50): InboxResponse? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$baseUrl/inbox?max=$max").get().build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> json.decodeFromString<InboxResponse>(resp.body?.string() ?: "{\"events\":[]}")
                        resp.code == 401 || resp.code == 403 -> { onAuthFailure?.invoke(); null }
                        else -> null
                    }
                }
            } catch (e: Exception) { null }
        }

    suspend fun ackInbox(eventId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = "{\"event_id\":\"$eventId\"}".toRequestBody(mediaTypeJson)
                val req = Request.Builder().url("$baseUrl/inbox/ack").post(body).build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> true
                        resp.code == 401 || resp.code == 403 -> { onAuthFailure?.invoke(); false }
                        else -> false
                    }
                }
            } catch (e: Exception) { false }
        }
    suspend fun resolveAssumptions(taskId: String, req: ResolveAssumptionsReq): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(req).toByteArray().toRequestBody(mediaTypeJson)
                val httpReq = Request.Builder()
                    .url("$baseUrl/tasks/$taskId/assumptions/resolve")
                    .post(body)
                    .build()
                client.newCall(httpReq).execute().use { resp ->
                    when {
                        resp.isSuccessful -> true
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "resolveAssumptions auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            false
                        }
                        else -> {
                            logger.e("ControlPlaneApi", "resolveAssumptions http=${resp.code}")
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("ControlPlaneApi", "resolveAssumptions error", e)
                false
            }
        }

    suspend fun getDevices(): List<HubDevice>? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("$baseUrl/devices").get().build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val body = resp.body?.string() ?: "[]"
                            json.decodeFromString<List<HubDevice>>(body)
                        }
                        resp.code == 401 || resp.code == 403 -> {
                            logger.e("ControlPlaneApi", "getDevices auth error ${resp.code}")
                            onAuthFailure?.invoke()
                            null
                        }
                        else -> null
                    }
                }
            } catch (e: Exception) { null }
        }

}
