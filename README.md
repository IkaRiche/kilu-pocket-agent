# KiLu Pocket Agent

**KiLu Pocket Agent** is the dual-role native Android client for the KiLu Network Cloud Control Plane. It allows users to turn any Android device into either a secure Approver (which cryptographic signs operation plans using its local Keystore) or an autonomous Hub Runtime (which automatically extracts web data based on authorized plans).

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
