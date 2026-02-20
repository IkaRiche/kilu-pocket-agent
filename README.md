# KiLu Pocket Agent

![GitHub release (latest by date)](https://img.shields.io/github/v/release/IkaRiche/kilu-pocket-agent?color=success&style=flat-square)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/IkaRiche/kilu-pocket-agent/release.yml?branch=main&style=flat-square)
![License](https://img.shields.io/github/license/IkaRiche/kilu-pocket-agent?style=flat-square)

**KiLu Pocket Agent** is the dual-role native Android client for the KiLu Network Cloud Control Plane. It allows users to turn any Android device into either a secure Approver (which cryptographic signs operation plans using its local Keystore) or an autonomous Hub Runtime (which automatically extracts web data based on authorized plans).

## Table of Contents
- [Architecture](#architecture)
- [Dual-Role System](#dual-role-system)
- [Repository Structure](#repository-structure)
- [Security Disclaimer](#security-disclaimer)
- [Deployment & Releases](#deployment--releases-v0x)
- [Contributing](CONTRIBUTING.md)

## Architecture

This application is built with:
*   **Language**: Native Kotlin.
*   **UI Framework**: Jetpack Compose and Navigation Compose.
*   **Network**: OkHttp and `kotlinx.serialization`.
*   **Security & Crypto**: AndroidX Security Crypto (`EncryptedSharedPreferences`) and local Ed25519 signing (via Bouncy Castle / Tink / Keystore).
*   **Computer Vision**: Google Play Services ML Kit for blazing-fast QR code scanning, and ZXing for QR code generation.

## Dual-Role System

1.  **APPROVER (Master Device)**
    *   Generates an identity keypair using the Android Keystore.
    *   Initiates Pairing to add Hubs.
    *   Receives tasks and prompts the user to biometrically approve constraints (e.g., domain limits, execution boundaries).
    *   Signs the `Plan` and submits the `WindowGrant` to the Cloud Control Plane.
    *   Receives execution results in the local Inbox.
2.  **HUB (Execution Worker)**
    *   Scans the Approver's QR Code to securely pair with the user's specific tenant.
    *   Silently polls the Control Plane queue for `READY_FOR_EXECUTION` tasks.
    *   Executes browser operations internally autonomously (via Android WebView and local JS evaluation).
    *   Escalates assumptions to the Approver when blocked (e.g. by new Captchas or Paywalls).

## Repository Structure

*   `app/src/main/java.../`
    *   `core/`: App-wide utilities, including OkHttp clients, API interceptors, Encrypted Preferences (`DeviceProfileStore`), Crypto operations, and QR scaffolding.
    *   `features/`: Specific UI domains including Onboarding (Welcome, Role Selection), Pairing (QR Display & Scanning).
    *   `shared/models/`: Strongly-typed Data Transfer Objects aligned with Cloud Control Plane JSON Schemas.

## Security Disclaimer

**"Thin client, thick cloud"**: The Pocket Agent relies entirely on the external Cloud Control Plane for state tracking, rate limiting, duplicate protection, and revocation checks. The pocket agent *does not* handle sensitive backend billing operations or API token provisioning directly.

## Deployment & Releases (v0.x)

The project leverages automated **GitHub Releases** matching tags like `v*` (e.g. `v0.3.6`).

### Release Checklist
Before creating and pushing a new tag, complete the following manually:
1. Ensure the `versionCode` and `versionName` in `app/build.gradle.kts` are updated strictly monotonically.
2. Edit `CHANGELOG.md` reflecting the new version exactly as formatted (helps the automated release extractor).
3. Test locally against the cloud-control-plane.
4. Push changes (`git commit -m "chore: bump to v0.3.6" && git push`).

### Triggering the Release
```bash
git tag v0.3.6
git push origin v0.3.6
```

The GitHub Actions workflow `.github/workflows/release.yml` will automatically:
1. Decode the secure Keystore securely injected via `ANDROID_KEYSTORE_B64`.
2. Ensure strict `versionCode` increments from prior build configs safely.
3. Build the `prodRelease` (`app-prod-release.apk`) pointing statically to `https://kilu-control-plane.heizungsrechner.workers.dev`.
4. Run `sha256sum` generating deterministic hashing via `SHA256SUMS.txt`.
5. Mount the artifacts to the GitHub Release.
