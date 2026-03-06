# Changelog

All notable changes to KiLu Pocket Agent.

## [0.8.6] — 2026-03-06

### Fixed
- **ApprovePlanReq Schema Sync**: `kotlinx.serialization` with `encodeDefaults = false` was stripping `biometric_present` and `pubkey_alg` fields because they had default values. Removed default values from `ApprovalReceipt`/`ApprovePlanReq` and explicitly enabled `encodeDefaults = true` so the payload exactly matches the JSON schema required by the cloud API.
- **Empty device_id check**: Added explicit validation to block approval and show an error if `device_id` is missing.

## [0.8.5] — 2026-03-06

### Fixed
- **ApprovePlanReq Schema Sync**: Server schema strictly requires `approval_receipt` to be a nested object containing the signature elements. `ApprovePlanReq` data class updated to exactly match this structure.
- **Card UI Bleed**: Fixed red background bleeding on swipe-to-dismiss in `ApproverTasksHomeScreen`.
- **Text Overflow**: Prevented long device names from pushing status text off-screen in `DevicesScreen`.

## [0.8.4] — 2026-03-06

### Fixed
- **ERR_SCHEMA_VALIDATION on Approve**: `ApprovePlanReq` was missing required `device_id` and `biometric_present` fields. Now passed correctly from `DeviceProfileStore`.
- **Task List - Swipe to Delete**: Tasks can now be cancelled by swiping left — shows a confirmation dialog before sending `POST /tasks/:id/cancel`.
- **UI Layout**: Buttons no longer cut off on PlanPreview and Diagnostics screens.

## [0.8.3] — 2026-03-06

### Fixed
- **Biometric Prompt**: `MainActivity` changed to `FragmentActivity` — biometric prompt now works correctly when approving plans.
- **UI Layout**: `PlanPreviewScreen` — plan steps now scroll vertically; Approve button is always visible at the bottom regardless of content length.
- **Protocol Idempotency**: `POST /v1/tasks/:id/plan` no longer returns `ERR_INVALID_STATE` when task is already in `NEEDS_PLAN_APPROVAL` state — returns existing plan instead.

## [0.8.2] — 2026-02-24

### Fixed
- **Build Failure CI**: Resolved a Kotlin compiler error (`Nullable receiver of type String?`) in `WebViewExecutor` that broke the `v0.8.1` pipeline.

## [0.8.1] — 2026-02-24

### Fixed
- **Critical Run-time Bug**: Fixed string interpolation escaping (`\$taskId` to `$taskId`) in `NavGraph` which caused tasks not to be found after creation, restoring full task approval flow.
- Minor log string interpolations repaired in `WakelockGuard`, `WebViewExecutor`, `CryptoUtils`.

## [0.8.0] — 2026-02-24

### Added
- UX overhaul for presentation and trust.
- Comprehensive Proof Docs (GUARANTEES, THREAT_MODEL, BENCHMARKS).
- Pairing fixes and secure keystore scrubbing.
- Local LLM advisory provider support (Optional).
- One-command quickstart `demo.sh`.

## [0.6.2] — 2026-02-22

### Fixed
- Build errors: replaced extended Material Icons (Shield, Devices, Inbox, ContentCopy, QrCode2) with base-set icons
- Null-safety: `task.external_url` in `HubRuntimeLoop` now handles nullable fields after model update

## [0.6.1] — 2026-02-22

### Added
- **Bottom Navigation** for Approver: Tasks / Devices / Settings tabs (`ApproverScaffold`)
- **DevicesScreen**: pairing status, collapsed IDs with Copy, "Pair a Hub Device" CTA, Diagnostics
- **SettingsScreen**: Control Plane URL, Switch Role, Reset Pairing with dialog, app version

### Changed
- `NavGraph`: `approver_home` now uses `ApproverScaffold` with bottom nav

## [0.6.0] — 2026-02-22

### Added
- **Design System**: `Color.kt` (dark/light palettes), `Type.kt` (typography), `KiluTheme.kt` (system theme, edge-to-edge)
- **Components**: `StatusChip`, `TaskCard`, `EmptyState`, `StatCard`

### Changed
- `MainActivity`: wrapped in `KiluTheme` + `enableEdgeToEdge()`
- `ApproverTasksHomeScreen`: complete rewrite — M3 Scaffold, TopAppBar, FAB, stat cards

## [0.5.1] — 2026-02-22

### Fixed
- `$errorMsg` literal text in all screens (project-wide search/replace, 0 remaining)
- Hub queue parsing: server returns `{items: [...]}`, client now uses `HubQueueListResponse` wrapper
- `HubQueueResponse` fields aligned with server (`active_grant_id`, optional fields)
- `HubTaskCard` escaped string interpolation

### API Note
`GET /v1/hub/queue` returns `{ items: [...] }` — client models updated.

## [0.5.0] — 2026-02-22

### Added
- `HubPairInitScreen`: dedicated Hub QR generation (separate from Approver registration)
- "Pair Hub" button on Approver Tasks screen
- `.gitignore` for repo

### Fixed
- `$errorMsg` literal text in `ApproverTasksHomeScreen`
- `QrGenerator` import path

### Changed
- `ApproverPairingInitScreen` title → "Register as Approver"
- NavGraph: 6 explicit routes with startup gate, removed conditional `pairing_home`

### Security
- Android signing keystore rotated (v2), `pass.txt` scrubbed from history
