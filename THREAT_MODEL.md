# THREAT MODEL (STRIDE)

Scope: KiLu Pocket Agent (Approver + Hub) + Control Plane (Cloudflare Worker).
Goal: constrain execution authority under adversarial inputs (prompt injection, malicious pages, replay attempts).

Assumptions:
- The user controls the Approver device and can provide biometric confirmation.
- Devices are not rooted and OS-level protections are intact (unless explicitly tested otherwise).
- Network is adversarial: assume MITM is possible unless TLS is enforced.
- The LLM (cloud or local) is **untrusted**.

Security objectives:
1) Hub executes **only** with valid, scoped, time-bounded capability tokens.
2) Sensitive actions require explicit human approval (biometric + signature).
3) Outputs are tamper-evident and auditable.
4) Replay attempts are rejected.

---

## STRIDE Table

| Threat | Attack Vector | Impact | Mitigation | Where enforced |
|---|---|---|---|---|
| Spoofing | Forged pairing QR / fake Control Plane | Hub pairs with attacker, token theft | Pairing offer must be signed; Hub verifies server signature + origin | Hub pairing flow + CP signing |
| Spoofing | Fake Approver device identity | Attacker signs approvals | Device keys + approval receipts verified | Approver signing + CP verify |
| Tampering | Modified plan steps between LLM and executor | Execute unintended action | Canonical intent hash + tokens bound to DSL hash / bindings | CP issuance + Hub validation |
| Tampering | Token payload changed (scopes/allowlist) | Escalation of privilege | Token signature verification + strict parsing | Hub validator |
| Repudiation | User denies approving action | Compliance/audit failure | Signed approval receipts stored with episode | CP audit store |
| Information Disclosure | Tokens leaked (logs, storage) | Unauthorized execution | Short TTL + binding + single-use JTI; avoid logging secrets | CP + Hub |
| Information Disclosure | Sensitive page content exfiltrated to cloud LLM | Privacy breach | Extract-only digest + minimization; optional local LLM | Hub |
| Denial of Service | Flood `/init` or `/queue` | Service degraded | Rate limiting; token bucket per IP/tenant | CP |
| Denial of Service | Force repeated LLM loops | Cost blow-up | Budgets, bounded retries; early escalation to human | CP policy + Hub runtime |
| Elevation of Privilege | Hub executes without approval | Unauthorized action | Fail-closed validation + approval-required policy | Hub + CP |
| Elevation of Privilege | Replay JTI | Reuse old capability | Single-use JTI + atomic consume | CP |

---

## High-Risk Scenarios & Required Invariants

### 1) Prompt injection on web pages
- The LLM is untrusted and may follow malicious instructions.
Required:
- Hub must be fail-closed (no token → no action).
- Tokens must be scoped (domain allowlist, action types).
- Gated actions require approval receipt.

### 2) Evil Maid pairing substitution
- Attacker replaces QR or intercepts pairing.
Required:
- Pairing offers signed by CP key; Hub verifies signature.
- Approver verifies CP key fingerprint / origin.

### 3) Token replay / double-spend
Required:
- Single-use JTI enforced in CP with atomic consume.
- Hub rejects tokens already consumed (if CP signals) or rejects reused nonce sequences.

### 4) “Nice UI, different payload”
- Hub shows one approval summary, but signs different canonical content.
Required:
- Approver displays canonical intent hash and signs canonical payload (JCS).
- CP verifies signature against the signed canonical payload.

---

## Out of Scope (for v0.x)
- Rooted device attacks, kernel-level compromises.
- Physical attacks that bypass biometric hardware.
- Side-channel extraction of keys from secure hardware.

---

## Reporting
See `SECURITY.md`.
