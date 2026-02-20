# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.6] - 2026-02-20

### Added
- Week 4 Release Packaging Workflow (.github/workflows/release.yml)
- Enforced HTTPS on Prod flavor via build config constraints and UI validation.
- `DEFAULT_CONTROL_PLANE_URL` hardened to the Cloudflare Worker.
- Release APK `app-prod-release.apk` generation pipeline yielding SHA256 sums securely.

### Changed
- Refactored `build.gradle.kts` introducing separate `prod` and `dev` environmental dimensions cleanly.
- Enforced `isDebuggable = false` correctly targeting the `prodRelease` build variant.
- Set `applicationIdSuffix = ".dev"` strictly isolating local/sandbox builds from canonical prod footprint cleanly.

## [0.3.5] - 2026-02-19

### Added
- Hub Always-On background runtime via explicitly scoped `LifecycleService`.
- Active execution limits preventing quota runaway loops inherently (i.e. strictly bound thermal wake-locks yielding 20 operations hourly).
