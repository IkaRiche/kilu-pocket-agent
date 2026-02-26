# GUARANTEES

This document describes **what KiLu guarantees**, **how each guarantee is enforced**, and **where to find proof** (code + tests).
If a row says “Planned”, it is a roadmap item, not a current guarantee.

Legend:
- Hub = Executor (Android device running the Hub role)
- Approver = Human Authority (Android device running the Approver role)
- Control Plane = Cloudflare Worker (policy + issuance + audit)

---

## Guarantees Table

| Property | What it means | Mechanism | Proof (code/tests) | Status |
|---|---|---|---|---|
| Fail-closed execution | Hub **refuses** to execute if no valid StepToken exists for the step | Hub validates StepToken (signature + bindings + exp) before any action | Code: `HubRuntimeLoop.kt` / `StepTokenValidator.kt` (or equivalent). Tests: `*_fail_closed_*` | **Must be true** to claim “fail-closed” |
| Replay-proof capabilities | StepTokens are **single-use**; replays are rejected | JTI single-use + storage + optimistic concurrency; exp enforced | Control Plane: `cloud/src/index.ts` (grant issuance + consume). Tests: `*_replay_*` | Enforced when JTI consumption is implemented |
| Time-bounded authority | Every StepToken expires; stale tokens cannot execute | `exp` checked on Hub and Control Plane | Hub validator + CP issuance/verify | Should be enforced |
| Domain / scope allowlist | Steps are constrained to approved domains/scopes | Policy engine binds token to allowlist + scope; Hub enforces | CP policy module + Hub validator | Should be enforced |
| Human-gated sensitive actions | Sensitive steps require biometric presence + signed receipt | Approver biometric gate + Ed25519 approval receipt; CP requires receipt for gated actions | Approver: `BiometricGate.kt`, signing module. CP: receipt verification | Should be enforced |
| Split-trust roles | Planner cannot execute; Executor cannot approve | Role separation + key separation | Architecture + runtime guards | Design invariant |
| Tamper-evident results | Outputs include evidence hashes that bind what was seen/done | Hub produces SHA-256 evidence hashes; CP stores them with episode | Hub executor + CP storage | Should be enforced |
| Deterministic canonicalization | What human approves is canonical, not free-form text | JCS canonicalization of intent/DSL; hash commitments | `cloud/src/canonicalize.ts` / `pkg/crypto/normalize.go` (if present) | Planned/partial |
| Version lockdown | Protocol versions are pinned; mismatches fail | Version pins + tests | `assurance/VERSIONS.json` + tests | Planned/partial |

Notes:
- If you rename/move files, update this table so the repo stays self-verifying.
- “Status” is deliberately strict: do not claim a guarantee publicly unless it’s proven by tests.

---

## Minimum “No-Overclaim” Rules

You MAY claim:
- “Split-trust architecture” (if the roles are implemented)
- “Designed for fail-closed” (until a fail-closed test exists)
- “Tamper-evident evidence hashes” (if evidence hashing is implemented)

You MUST NOT claim:
- “Hardware-backed / Secure Enclave keys” unless you use **non-exportable Android Keystore/StrongBox keys** and can prove it.

---

## Evidence & Proof Conventions

Every guarantee should have:
1) A **positive test**: it works when inputs are valid.
2) A **negative test**: it fails when the guarantee is violated (missing token, bad sig, expired token, replayed JTI).
3) An observable **artifact**: log line, receipt, stored hash, or structured event.

---

## Recommended Tests (to add if missing)

- `fail_closed_missing_token`: Hub must refuse execution.
- `fail_closed_invalid_sig`: Hub must refuse execution.
- `replay_rejected_same_jti`: CP must reject second consume of the same JTI.
- `expired_token_rejected`: Hub and CP reject exp < now.
- `approval_required_scope`: CP refuses gated action without approval receipt.
- `approval_receipt_sig_valid`: receipt signature must verify against Approver device key.

---

## Disclosure

See `SECURITY.md` for reporting vulnerabilities.
