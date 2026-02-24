# ⚡ Benchmarks

*Current as of version v0.7.x.*

KiLu Pocket Agent is optimized for minimal overhead. Due to the "thin client" architecture, performance metrics are largely bound by device network latency and the Cloud Control Plane (Cloudflare Workers) execution times. 

## 1. Cryptographic Overhead (Approver)

| Operation | Environment | Avg Time (ms) | Notes |
| :--- | :--- | :--- | :--- |
| Keypair Generation (Ed25519) | Pixel 6 (Android 14) | ~15ms | One-time per installation |
| Payload Canonicalization | Pixel 6 (Android 14) | < 2ms | JSON RFC 8785 |
| Ed25519 Signature Generation | Pixel 6 (Android 14) | < 5ms | Bouncy Castle |
| **Total Approval Latency** | **Pixel 6 (Android 14)** | **< 10ms** | *Excluding Biometric UI prompt time* |

## 2. Token & Step Governance (Hub)

The Hub executes operations by polling the CCP for `READY_FOR_EXECUTION` tasks. 

| Operation | Constraint | Enforcement Mechanism |
| :--- | :--- | :--- |
| **Max Task Payload Size** | 6,000 chars | Strict `CreateTaskReq` Schema Validation |
| **Polling Interval** | 3-5 seconds | Client-side configurable delay loop |
| **Step Execution Timeout** | 30 seconds | Client WebView timeout (escalates failure to Inbox) |
| **Max Concurrent Tasks** | 1 | Strictly serialized queue to prevent DoS |

## 3. Network Lifecycle 

Measured on a standard 4G/LTE connection against the production CCP (`kilu-control-plane.heizungsrechner.workers.dev`):

*   **Pairing Handshake:** ~120ms round-trip (QR Scan -> Token Provision -> KV Store Commit).
*   **Task Fetch (Poll):** ~40-60ms.
*   **Submit Result:** ~80ms (includes CCP signature validation and Database write).

*Note: Automated, CI-integrated latency benchmarking suites will be introduced in upcoming releases prior to v1.0.*
