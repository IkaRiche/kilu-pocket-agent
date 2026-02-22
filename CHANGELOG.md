# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.3] - 2026-02-22

### Fixed
- Cryptographic Handshake: `HashingUtil` now signs UTF-8 bytes of full `sha256:<hex>` string, matching the server's `TextEncoder().encode()` behavior.
- Server Signature Verification: Replaced placeholder `SERVER_PUBKEY_B64` with real Ed25519 public key derived from worker seed.
- Device Confirm: Both Approver and Hub confirm flows now produce signatures the server can verify.

## [0.4.2] - 2026-02-21

### Fixed
- Pairing Serialization: Resolved "missing fields [offer_core_hash, expires_at]" error by aligning backend response with client model.
- Client Resilience: Added fallback parsing for pairing responses to remain robust against nested vs top-level fields.

## [0.4.1] - 2026-02-21

### Fixed
- Build Error: Restored missing `KeyManager` import in `DiagnosticsScreen.kt` and fixed `Base64` ambiguity.

## [0.4.0] - 2026-02-21

### Fixed
- Authentication Errors: Resolved `ERR_UNAUTHENTICATED` (401) by fixing double `/v1/v1` prefixing in API calls.
- Routing Confusion: Added root health check to the Control Plane for deployment verification.
- URL Normalization: Better handling of trailing slashes and versioned origins in the mobile app.

### Changed
- Refactored `ApiClient` to use a centralized `apiUrl()` helper and normalized origin storage.
- Updated `Diagnostics` UI to show both Control Plane Origin and API Base URL.

## [0.3.9] - 2026-02-21

### Added
- Gemini Planner v0: Intelligent plan generation with managed key support.
- Plan Previews: AI-generated summaries and detailed step previews in the UI.
- Quota Management: Real-time display of planner and report credits/daily limits.
- On-demand Reporting: Approver-triggered AI summarization for completed tasks.

## [0.3.8] - 2026-02-21

### Fixed
- Diagnostics: Securely generate KeyPair for device initialization.
- Network: Enforce JSON `Content-Type` for POST initialization requests.

## [0.3.7] - 2026-02-20

### Changed
- Incremented `versionCode` to 37 for canonical release tracking.
- Implemented persistent JKS keystore for release signatures.
