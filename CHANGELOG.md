# Changelog

All notable changes to KiLu Pocket Agent.

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
