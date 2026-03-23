# Roadmap — kilu-pocket-agent

## Current Version: v0.9.29

## Shipped

| Version | What | Status |
|---|---|---|
| **v0.x** | Dual-node (Approver/Hub), QR pairing, Ed25519, WebView sandbox | ✅ Done |
| **v0.9.3** | Window insets / edge-to-edge baseline across all screens | ✅ Done |
| **v0.9.4** | CI compile fix (`windowInsets`→`contentWindowInsets`, stray brace) | ✅ Done |
| **v0.9.5** | Hub task routing fix (heartbeat, eligible-runtime selection, auto-bind, getTask parsing) | ✅ Done |
| **v0.9.29** | Auto-refresh task list every 10s | ✅ Done |
| **R1 Baseline** | **E2E smoke test 3/3 passed** — Telegram DONE notification confirmed, system considered core-stable | ✅ **2026-03-23** |

## Phase 1: Hub Routing & Presence (v0.9.5)

- [x] `HubRuntimeLoop`: `registerRuntime()` on startup + 5-min heartbeat → `hub_runtimes.status=ONLINE`
- [x] `DeviceProfileStore`: `getRuntimeId()` / `getToolchainId()` persisted at pairing
- [x] `HubOfferDetailsScreen`: save `runtime_id` + `toolchain_id` from confirm response
- [x] `ControlPlaneApi.getTask()`: parse bare object (not `Map["task"]` envelope)
- [x] `ControlPlaneApi.registerRuntime()`: new heartbeat method

## Phase 2: UI / Design Hardening (v0.9.6)

- [ ] Hub Dashboard: KiluStatBadge, proper empty state, queue count indicator
- [ ] Diagnostics screens: dark background, KiluCard layout
- [ ] Apply `KiluPrimaryButton` + `KiluTopBar` to remaining screens
- [ ] `navigationBarsPadding()` verified on all bottom action screens

## Phase 3: Fail-Closed Enforcements (v1.0)

- [ ] Strict execution boundary enforcement
- [ ] Reproducible benchmark suite
- [ ] Capability token expiry (JTI) on Android

## Phase 4: Local AI

- [ ] Local LLM provider interface for compression/planning
- [ ] Support for entirely cloud-key-free operation
- [ ] Open-source self-hosted Control Plane drops

## Phase 5: Enterprise

- [ ] SIEM Webhooks
- [ ] KMS Integration for external signing
- [ ] ISO Compliance mapping templates
