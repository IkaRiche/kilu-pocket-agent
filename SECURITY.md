# Security Policy

## Supported Versions
Only the latest major version (`v0.x`) receives active security patches.

## Reporting a Vulnerability

Security is a primary concern for the **KiLu Pocket Agent** as it handles biometric approvals and autonomous extraction operations. 

If you discover a potential vulnerability, please **DO NOT** create a public GitHub issue. 

Instead, report it privately to our security team via email at `security@kilunetwork.com`. We will acknowledge receipt within 48 hours and provide an estimated timeline for remediation.

### Qualitative Risks
Bugs related to:
- Ed25519 Keystore Signature spoofing
- Unauthorized escalation of Hub Extraction scopes
- WebView sandbox escapes
...should be reported immediately.
