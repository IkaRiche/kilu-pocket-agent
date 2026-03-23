# STATUS.md — kilu-pocket-agent

> This file tracks the status of the **Android authority layer** only.  
> Project-level status (phases, governance, baselines) lives in [KiLu-Network/STATUS.md](https://github.com/IkaRiche/KiLu-Network/blob/main/STATUS.md).

---

## Current State — 2026-03-23

**R1 core path: ✅ STABILIZED**

End-to-end smoke test passed (3/3 consecutive runs, no manual intervention).

### Android Component Status

| Component | State | Notes |
|---|---|---|
| Approver pairing | ✅ Working | Ed25519 keygen + QR ceremony |
| Task creation | ✅ Working | Free text → Control Plane |
| Plan approval (biometric) | ✅ Working | Ed25519 signing via Android Keystore |
| Hub device registration | ✅ Working | QR pairing + runtime binding |
| Hub task queue polling | ✅ Working | Runtime-bound + grant-gated |
| Step token minting | ✅ Working | `mint-step-batch` path |
| Hub WebView executor | ✅ Working | Constrained to allowlist_domains |
| Device list screen | ✅ Working | Approver + Hub devices shown |
| Auto-refresh task list | ✅ Working | Every 10s |

### Runtime Classification

- **Android Hub** = Validation runtime (demos, E2E proof)  
- **Android Approver** = Authority device (production role)  
- **Linux Hub** = Production runtime path (planned, R2)

### Known Issues

| Issue | Severity | Notes |
|---|---|---|
| `registerRuntime` returns 404 on re-paired devices | Low | Logged, Hub continues working. UPSERT fix planned. |
| Stale device/runtime entries in D1 | Low | Accumulates on re-pair. Cleanup planned R1. |

---

## Confirmed Baseline

**Canonical baseline: `r1-core-stable-2026-03-23`** (git tag on this repo)

3 consecutive E2E runs confirmed:

| Run | Task | Site | DONE notification |
|-----|------|------|------------------|
| 1 | tsk_00c14c40 | heizungsrechner.de → klimacoach.com | ✅ 10:03 |
| 2 | tsk_f6bd27ff | orf.at | ✅ 10:26 |
| 3 | tsk_0e5665 | bbc.com | ✅ 10:30 |

No D1 edits, no service restarts, no re-pairing between runs.
