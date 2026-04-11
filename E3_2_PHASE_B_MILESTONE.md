# E3.2 Phase B — Workflow Grant Orchestration: MILESTONE COMPLETE

**Closed:** 2026-04-11  
**APK:** `kilu-agent-prod-v0.9.35.apk` (versionCode 125)  
**Control Plane:** `kilu-control-plane` (Cloudflare Workers + D1)

---

## Summary

E3.2 Phase B implements real CP-backed workflow grants — a bounded authority primitive that allows a single biometric tap to atomically approve a pre-declared, sealed set of tasks. Both the happy path (B9) and the failure/revoke path (B10) have been live-verified on an Android device against the production Control Plane.

---

## What Was Proven Live

### B9 — Happy Path (2026-04-11)

- Bridge pre-allocated 3 CP tasks (`PLANNING` status)
- Computed `workflow_ref = sha256(sealed payload)` client-side
- CP issued workflow grant `wfg_...` with independently recomputed `workflow_ref`
- Bridge stamped `workflow_grant_id` on all pre-allocated tasks immediately after grant issuance
- Android Approver displayed **"WORKFLOW GRANTS (1 PENDING APPROVAL)"** section
- User opened `WorkflowGrantDetailScreen`: Status `ACTIVE`, runtime, toolchain, 3 sealed task IDs, seal invariants
- User tapped **APPROVE ALL** → biometric → Ed25519 signature
- CP atomically transitioned all 3 tasks: `PLANNING → READY_FOR_EXECUTION`
- Bridge executed all 3 steps **without per-step approval**
- `grant_consumed_at` set on completion

### B10 — Revoke Path (2026-04-11)

- New 3-step grant issued and approved via APPROVE ALL
- Step 1 completed successfully (`DONE`)
- Step 2 injected with `FAILED` + `failure_code=ERR_B10_INJECTED_FAILURE`
- Bridge detected `pollResult.kind === 'failed'`
- Bridge called `POST /v1/workflow-grants/:id/revoke`
- CP set grant `status='revoked'`, `revoked_at=2026-04-11T09:33:17Z`
- Step 3 task remained `READY_FOR_EXECUTION` — **not touched**
- Bridge logged: `Workflow complete outcome=partial steps=1/3`
- `grant_consumed_at` was `undefined` (revoked, not consumed)

---

## Security Invariants Confirmed

| Invariant | Status |
|-----------|--------|
| No dynamic step expansion after approval | ✅ Sealed at grant issuance |
| Single runtime binding — no mid-workflow migration | ✅ `target_runtime_id` locked |
| Tamper detection on `workflow_ref` | ✅ CP recomputes independently; mismatch = hard abort |
| Terminal grants cannot be reused | ✅ `revoked` status blocks re-approve |
| Revoke stops all further execution | ✅ Remaining steps not touched after revoke |
| `grant_consumed_at` null on revoke | ✅ Confirmed in D1 |

---

## CP Endpoints Delivered

| Endpoint | Purpose |
|----------|---------|
| `POST /v1/workflow-grants` | Create + seal grant |
| `GET /v1/workflow-grants/:id` | Status poll + full detail (task_ids, runtime, toolchain) |
| `POST /v1/workflow-grants/:id/tag-tasks` | Stamp `workflow_grant_id` on pre-allocated tasks |
| `POST /v1/workflow-grants/:id/approve` | One-tap atomic activation |
| `POST /v1/workflow-grants/:id/revoke` | Bridge revokes on step failure |
| `POST /v1/workflow-grants/:id/step-done` | Consumed step counter |

---

## Android UI Delivered (B7)

- **Home screen:** `WORKFLOW GRANTS (N PENDING APPROVAL)` section with Review cards
- **WorkflowGrantDetailScreen:** Status, Runtime, Toolchain, Steps, TTL, `workflow_ref` seal, sealed step list, seal invariants callout
- **APPROVE ALL:** biometric-gated, Ed25519 signed, same security path as per-step approval
- **Routing:** tasks with `workflow_grant_id` bypass legacy `PlanPreviewScreen`

---

## Bridge Orchestration Delivered (B5/B6)

- `requestWorkflowGrant()` — real `POST /v1/workflow-grants` with client-side `workflow_ref`
- `workflow_ref` mismatch → hard abort (tamper detection)
- `tagTasksWithGrantId()` — stamps `workflow_grant_id` immediately after grant issuance
- Poll activation loop (900s timeout)
- Sequential step execution **without per-step approval** after grant activation
- Revoke on any step failure
- Fallback to per-step mode if CP returns 404/unsupported
- `grant_consumed_at` set on full completion, `undefined` on revoke

---

## What Is Not In Scope (Intentional)

- Multi-hub routing (single `target_runtime_id` per grant)
- Partial retry after step failure (revoke is terminal)
- Grant extension / TTL renewal
- Multiple concurrent grants for a single workflow

These are post-E3.2 concerns. The current model is intentionally conservative.

---

## Next Directions

After this milestone, two natural paths open:

1. **Post-E3.2 baseline freeze** — canonize the Phase B model as a stable foundation
2. **E4** — next orchestration capability (TBD: multi-hub, memory-backed workflows, etc.)
