<div align="center">

# 📱 KiLu Pocket Agent

**The native Android client for the KiLu Network Cloud Control Plane.**

[![Release](https://img.shields.io/github/v/release/IkaRiche/kilu-pocket-agent?color=success&style=for-the-badge)](https://github.com/IkaRiche/kilu-pocket-agent/releases/latest)
[![CI/CD Status](https://img.shields.io/github/actions/workflow/status/IkaRiche/kilu-pocket-agent/release.yml?branch=main&style=for-the-badge&logo=github)](https://github.com/IkaRiche/kilu-pocket-agent/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0+-green.svg?style=for-the-badge&logo=android)](https://www.android.com/)
[![License](https://img.shields.io/github/license/IkaRiche/kilu-pocket-agent?style=for-the-badge)](LICENSE)

[Features](#-key-features) • [Architecture](#-architecture) • [Installation](#-installation) • [Dual-Role System](#-dual-role-system) • [Security](#-security-model)

</div>

---

## 💡 Overview

**KiLu Pocket Agent** turns any Android device into an active node on the KiLu Network. It operates in a unique **Dual-Role System**, allowing a device to act either as a completely secure **Approver** (cryptographically signing operations) or as an autonomous **Hub Runtime** (executing authorized web data extraction tasks).

Built entirely with native Kotlin and Jetpack Compose, the Pocket Agent delivers blazing-fast performance, deep OS integration, and uncompromising cryptographic security.

## ✨ Key Features

*   🛡️ **Hardware-Backed Security:** Utilizes AndroidX Security Crypto and the Android Keystore for secure Ed25519 identity generation and plan signing.
*   🔄 **Dual-Node Architecture:** Instantly switch between an administrative Approver interface and a silent, background Hub worker.
*   📱 **Native Performance:** Written in 100% Kotlin using the latest Jetpack Compose UI paradigms.
*   📷 **Rapid Pairing:** Seamless device pairing using Google Play Services ML Kit for instantaneous QR code scanning.
*   🔒 **Biometric Authentication:** Requires physical biometric verification (fingerprint/face) prior to authorizing any operational bounds.
*   🌐 **Autonomous Execution:** Hub devices evaluate JS and perform browser operations internally via Android WebView without external dependencies.

## 🏗️ Architecture

The application is modularly structured to enforce separation of concerns and maintain a "thin client, thick cloud" philosophy.

*   **`core/`**: App-wide network utilities (OkHttp), state management, Encrypted Preferences (`DeviceProfileStore`), cryptographic operations, and QR scaffolding.
*   **`features/`**: Isolated UI domains:
    *   `onboarding`: Welcome flows and Node Role selection.
    *   `pairing`: QR Display (Approver) & Scanning (Hub).
    *   `approver`: Task Inbox, Plan Preview, and Biometric Signing.
    *   `hub`: Background polling and execution engine.
*   **`shared/`**: Strongly-typed Data Transfer Objects (DTOs) utilizing `kotlinx.serialization` to strictly mirror Cloud Control Plane JSON schemas.

## 🎭 Dual-Role System

The Pocket Agent network relies on two distinct identities:

### 1. The Approver (Master Node)
The command center. 
* Generates a unique Ed25519 identity keypair anchored in the Android Keystore.
* Generates pairing QR codes to onboard Hub workers.
* Reviews incoming execution `Plan`s.
* Prompts the user for biometric authorization to sign execution boundaries (e.g., domain whitelists).
* Submits the cryptographic `WindowGrant` to the Cloud Control Plane.

### 2. The Hub (Execution Node)
The worker engine.
* Scans an Approver's QR Code to securely bind to a specific tenant identity.
* Polls the Control Plane for `READY_FOR_EXECUTION` tasks.
* Autonomously executes tasks sequentially.
* Escalates edge-case assumptions (like solving captchas or handling login prompts) back to the Approver.

## 🚀 Installation

### Download the Latest APK
You can always find the latest automatically built, signed, and hashed production APK in the [Releases](https://github.com/IkaRiche/kilu-pocket-agent/releases) tab.

### Build from Source
If you prefer to build the agent yourself:

```bash
# Clone the repository
git clone https://github.com/IkaRiche/kilu-pocket-agent.git
cd kilu-pocket-agent

# Build the development Debug APK
./gradlew assembleDevDebug

# (Optional) Build the Production Release APK
./gradlew assembleProdRelease
```

## 🔐 Security Model

**"Thin client, thick cloud."**

The Pocket Agent is intentionally designed to hold minimal state. 
* **No local billing logic**: All rate limiting, quota enforcement, and tier checking happens exclusively on the Cloud Control Plane.
* **No persistent API tokens**: Execution tokens are granted strictly per-task and revoked upon completion.
* **No key extraction**: Private keys never leave the Android Hardware-Backed Keystore. Signatures are generated entirely within the Secure Enclave.

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on how to submit pull requests, report bugs, and suggest new features.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
<div align="center">
  <sub>Built with ❤️ by the KiLu Network Team</sub>
</div>
