# Control Plane API Test Coverage Matrix

This document tracks the JVM testing coverage for all endpoints handled by the KiLu Android Agent (`ControlPlaneApi`).

| Endpoint | Method | Path Check | Happy path | Auth fail (401/403) | Malformed JSON | Empty body | Wrong schema | State errors |
|---|---|---|---|---|---|---|---|---|
| **Poll Queue** | GET | `/v1/hub/queue?max=1` | ✅ | ✅ (token cleared) | ✅ (bare array catch) | ✅ | ✅ (`items: null`, string) | ✅ (empty queue) |
| **Approve Plan**| POST | `/v1/plans/{id}/approve` | ✅ (request body verified) | ✅ (token cleared) | ✅ | ✅ | ✅ (missing `grant_id` fail-closed) | ✅ (approve without plan = 400 null) |
| **Mint Steps** | POST | `/v1/grants/{id}/mint-step-batch` | ✅ | ✅ (token cleared) | ✅ | ✅ | ✅ | ✅ (missing/exhausted grant = null) |
| **Submit Result**| POST | `/v1/tasks/{id}/result`| ✅ | ✅ (token cleared) | ✅ (HTTP 400 = false) | ✅ (HTTP 500 = false) | ✅ | ✅ (submit twice HTTP 409 = success) |

### Key Contract Validations
1. **Serialization Form**: Verifies that standard defaults (like `biometric_present = true`) are NOT stripped during serialization. Ensured by Golden Serialization tests.
2. **Wrapper Structures**: Verifies the agent correctly processes wrapped list responses (like `{ "items": [...] }` for `hub/queue`).
3. **Idempotency Standards**: `submitResult` expects and handles HTTP 409 Conflict as a successful idempotent state (meaning task is already closed).
4. **Session Clearing Uniformity**: All API calls reliably trigger `onAuthFailure` callback to purge session state immediately upon 401 or 403 responses.
5. **Fail-Closed Policy**: Handled by `MalformedResponseTest.kt`. The SDK NEVER crashes on HTML proxy pages, empty bodies, string bodies mapped to lists, or missing mandatory JSON fields.
