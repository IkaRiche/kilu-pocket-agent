# 🛡️ Guarantees

KiLu Pocket Agent is built on strict cryptographic and architectural guarantees. We operate under a verifiable "Zero Trust" model where every action is mathematically bound.

## 1. Split-Trust Architecture
**Property:** The Execution Engine (Hub) cannot unilaterally perform any action on behalf of the user without explicit, point-in-time cryptographic consent from the Authentication Engine (Approver).
**Mechanism:** 
* The Approver node generates and holds an Ed25519 keypair within an encrypted enclave (`DeviceProfileStore.kt`).
* The Hub node is cryptographically paired to a specific session token via QR code but holds no long-term identity keys.
* Execution tasks (`Plan.schema.json`) must be fetched and signed by the Approver, producing a `WindowGrant`. The Hub validates this grant before acting.
**Proof:** 
* `features/approver/PlanPreviewScreen.kt` - Biometric boundary approval and cryptographic signing.
* `cloud/src/crypto.ts` - Cloud Control Plane validation of the `WindowGrant`.

## 2. Hard Capability Bounds
**Property:** An authorized Hub session is strictly constrained to the parameters (domain, path, HTTP methods) explicitly approved by the user.
**Mechanism:**
* Executions are gated by a `WindowGrant` which explicitly lists allowed capabilities (e.g., `Capabilities.schema.json`: `allowed_domains: ["example.com"]`).
* Any deviation or attempt to access unauthorized domains by the executing JS sandbox is intercepted and blocked at the network level by the Cloud Control Plane proxy/egress.
**Proof:**
* `features/hub/HubDashboardScreen.kt` - Polling and strict adherence to the authorized `Task`.

## 3. Tamper-Evident Ledgers
**Property:** All decisions (Approval/Denial) and execution results are auditable and tamper-evident.
**Mechanism:**
* Every `WindowGrant` and `SubmitResultReq` includes the deterministic Ed25519 signature of the payload.
* The Cloud Control Plane validates the signature and standardizes the payload recursively before committing it to the database (`index.ts`).
**Proof:**
* `cloud/src/index.ts` - The `POST /v1/grants` endpoint strictly validates `canonicalize(payload) + signature`.

---

*Note: While development is ongoing, keypair generation currently utilizes `EncryptedSharedPreferences`. True hardware-bound non-exportable Keystore integration is on our immediate transition roadmap for v1.0.*
