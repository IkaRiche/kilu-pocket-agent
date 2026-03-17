# KiLu Pocket Agent
**The end of god-mode agents.**  
A split-trust mobile agent where **the cloud can think**, but **the phone can only act with cryptographic authority**.

> KiLu separates *brains* from *hands*:  
> **Hub** executes in a constrained sandbox.  
> **Approver** holds keys and confirms intent with biometrics.  
> **Control Plane** enforces deterministic policy and issues single-use capability tokens.

<p align="center">
  <!-- Replace with a real GIF or short mp4 poster -->
  <img src="assets/hero.gif" width="720" alt="KiLu demo: attack → blocked → approve → evidence" />
</p>

<p align="center">
  <a href="https://github.com/IkaRiche/kilu-pocket-agent/releases/latest"><img alt="version" src="https://img.shields.io/github/v/release/IkaRiche/kilu-pocket-agent?color=purple&style=flat-square"></a>
  <a href="https://github.com/IkaRiche/kilu-pocket-agent/actions"><img alt="build" src="https://img.shields.io/github/actions/workflow/status/IkaRiche/kilu-pocket-agent/release.yml?branch=main&style=flat-square"></a>
  <a href="SECURITY.md"><img alt="security" src="https://img.shields.io/badge/security-by--design-black?style=flat-square"></a>
  <a href="LICENSE"><img alt="license" src="https://img.shields.io/github/license/IkaRiche/kilu-pocket-agent?style=flat-square"></a>
</p>

---

## Why this exists
Most "agent frameworks" implicitly grant the LLM **god-mode**: unlimited tool access, long feedback loops, and high-variance behavior.

KiLu is built for the opposite:
- **Authority is explicit** (capability tokens + human signatures).
- **Execution is constrained** (Hub refuses without cryptographic mandate).
- **Outcomes are auditable** (evidence hashes + receipts).

If you care about **security**, **predictable cost**, and **verifiable intent**, this architecture is the point.

---

## What you get
- **Split-trust**: Planner ≠ Executor ≠ Human authority.
- **Fail-closed execution**: no valid StepToken → no execution.
- **Replay protection**: single-use JTI + expiry on every capability.
- **Tamper-evident results**: evidence hashes bound to each step/result.
- **Human-gated**: approvals require biometric presence + signature.
- **LLM-agnostic**: works with cloud LLMs or local models (see [Local LLM mode](#local-llm-mode-no-paid-api-keys)).

> Important: KiLu does **not** rely on "trust the model". It relies on **cryptographic constraints**.

---

## Architecture (Zero-Trust Split-Trust)
```mermaid
flowchart LR
  subgraph Cloud["☁️ Control Plane (policy + issuance)"]
    P1["Policy Engine\n(normalization, allowlist, budgets)"]
    P2["Capability Minting\nStepToken(JTI, exp, scopes)"]
    P3["Audit Store\n(episodes, receipts, evidence hashes)"]
  end

  subgraph Hub["📱 Hub (Executor)"]
    H1["WebView Sandbox\nExtract-only digest"]
    H2["StepToken Validator\nfail-closed"]
    H3["Evidence Builder\nsha256 artifacts"]
    H4["(Optional) Local LLM\ncompress/plan/risk-hints"]
  end

  subgraph Approver["📱 Approver (Human Authority)"]
    A1["Key Store\n(device-local keys)"]
    A2["Biometric Gate\npresence check"]
    A3["AVO Review Card\ncanonical intent + scope"]
    A4["Signed Approval Receipt\nEd25519"]
    A5["(Optional) Local LLM\nexplain + risk hints"]
  end

  P1 --> P2
  P2 -->|StepToken batch| H2
  H1 -->|digest + hashes| P1
  H2 --> H1
  H2 -->|execute| H1
  H3 -->|evidence hash + result| P3
  P3 -->|AVO request| A3
  A2 --> A4
  A4 -->|approval receipt| P3
  A3 --> A2
## Current Proven Baseline: Live Verified Flow

The entire authority-bound execution lifecycle has been live-verified on the production-grade control plane:

1.  **Pairing ceremony** — Approver and Hub devices bound to tenant via Ed25519.
2.  **Task creation** — Planning initiates; task enters `PLANNING` state.
3.  **Human approval** — Biometric-gated AVO review result in `READY_FOR_EXECUTION`.
4.  **Hub polling** — Go/Android Hub fetches the ready task with active grant.
5.  **Token minting** — Hub mints execution batch; task moves to `EXECUTING` (atomic).
6.  **Secure execution** — Action runs within constraint envelope (shell/webview).
7.  **Evidence submission** — Signed results + hashes committed to the ledger.
8.  **Terminal state** — Task completes as `DONE` with immutable evidence.

This flow ensures that agents do not get execution authority from queue visibility; authority is minted explicitly and bound to runtime/toolchain context.

---

## Three guarantees (with proof)

1. **Fail-closed**: without a valid StepToken, Hub refuses execution.
2. **Replay-proof**: each capability is single-use (JTI) and time-bounded (exp).
3. **Tamper-evident**: every output is bound to evidence hashes and receipts.

**See the Proof Docs:**
- [GUARANTEES.md](GUARANTEES.md) (property → mechanism → proof/tests)
- [THREAT_MODEL.md](THREAT_MODEL.md) (STRIDE + mitigations)
- [BENCHMARKS.md](BENCHMARKS.md) (token/step governance)
- [SECURITY.md](SECURITY.md) (disclosure + supported versions)

---

## Quickstart (10 minutes)

### 0) Prereqs
Two Android devices (or one device + emulator):
*   **Hub** (always-on executor)
*   **Approver** (keys + biometrics)
*   A running Control Plane (Cloudflare Worker or local dev)

### 1) Control Plane (Cloudflare Worker)
```bash
# example
cd cloud
npm install
npx wrangler dev
```

### 2) Android app
```bash
# example (Android Studio)
./gradlew assembleDebug
```
Install on both devices.

### 3) Configure Control Plane URL
On both Hub and Approver:
*   Set Control Plane URL (must be https in prod mode)
*   Verify "Diagnostics" shows the correct API base.

### 4) Pair Approver → Pair Hub
*   **On Approver:** Register as Approver (creates device identity)
*   **On Approver:** Devices → "Pair a Hub" (generates QR)
*   **On Hub:** Scan QR → Confirm & Connect

### 5) Run a demo episode
*   Create a simple read-only extraction task
*   Approve if requested
*   Hub executes, submits result + evidence hash

---

## Local LLM mode (no paid API keys)

KiLu can run without paid cloud LLMs by using local inference for:
*   digest compression
*   draft plan generation (as a proposal)
*   risk hints / injection flags
*   approval explanations on Approver

Control Plane remains in Cloudflare (cheap, stable) for:
*   deterministic policy
*   capability issuance (JTI/exp/scopes/bindings)
*   replay protection + audit logs

**Recommended topology**
*   **Hub:** local LLM (primary)
*   **Approver:** optional local LLM (approval copilot)

**"Authority" rule**
Local LLM outputs are advisory. Only these artifacts authorize execution:
1.  **StepToken** (Control Plane signature)
2.  **Approval receipt** (Approver signature, if required)

---

## Token efficiency (why KiLu is cheaper to operate)

Most agent stacks burn tokens due to: full-page context flooding (HTML/screenshots), long "think loop" retries, and re-reading unchanged pages.

KiLu reduces cost structurally:
*   extract-only digest (compact, deterministic)
*   bounded episodes (budgets + fail-closed)
*   dedupe + caching (no reprocessing unchanged states)
*   early escalation (human circuit-breaker)

See [BENCHMARKS.md](BENCHMARKS.md) for reproducible measurements.

---

## Security model (high level)

KiLu is designed for adversarial conditions: prompt injection, malicious webpage content, replay attempts, "evil maid" pairing substitution, and accidental overreach by the LLM.

**Threat model and mitigations:**
*   [THREAT_MODEL.md](THREAT_MODEL.md)
*   [SECURITY.md](SECURITY.md)

*NOTE on device keys: current builds may use device-local keys protected at rest (EncryptedSharedPreferences). A planned/optional upgrade path is non-exportable Android Keystore/StrongBox when enabled and verified.*

---

## Repo map
*   `app/` — Android app (Approver + Hub roles)
*   `cloud/` — Control Plane (Cloudflare Worker)
*   `docs/` — architecture, threat model, guarantees, benchmarks
*   `assets/` — diagrams, screenshots, hero GIF

## Roadmap
*   **v0.x:** UX polish, strict fail-closed enforcement + tests, reproducible benchmarks
*   **v1.x:** local LLM provider interface, self-hosted control plane option
*   **Enterprise:** SIEM hooks, KMS integration, compliance mapping

See [ROADMAP.md](ROADMAP.md).

## Contributing
We welcome PRs, but we take security seriously. 
*   Read [CONTRIBUTING.md](CONTRIBUTING.md)
*   Report vulnerabilities via [SECURITY.md](SECURITY.md)

## License
Apache-2.0. See [LICENSE](LICENSE).
