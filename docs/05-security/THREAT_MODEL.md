# Threat model (STRIDE)

**Purpose/scope:** initial design threat analysis for target system; not a penetration test. Assumptions: public browser/API, cloud control plane, team CI; no actual deployment. Ratings are initial qualitative High/Medium/Low and require review as code/cloud changes. Related: security architecture/baseline, SEC requirements.

| Asset / surface | Actor and STRIDE scenario | Impact / likelihood / risk | Mitigation | Verification |
|---|---|---|---|---|
| User/order data; API | External attacker (Spoofing): credential stuffing/session theft | account/data loss / M / High | MFA for administrators, rate limits, strong session lifecycle, credential hashing, alerting | auth abuse tests, config review |
| Order/admin actions | User (Elevation): IDOR or role bypass | fraud/privacy / M / High | deny-by-default object+function authorization, ownership checks | negative role/tenant tests |
| API / DB inputs | External attacker (Tampering): injection or malformed payload | data corruption / M / High | validation, parameterized persistence, bounded payloads | fuzz/negative integration tests |
| API response/logs | Attacker or insider (Information disclosure): error, log or metadata leak | personal/secret exposure / M / High | error allowlist, redaction, safe health/info endpoints | response/log scan |
| CI workflow / registry | Supply-chain attacker (Tampering/Elevation): poisoned dependency/action or workflow permissions | artifact compromise / M / High | immutable action refs, lockfiles, minimal permissions, scans, provenance, protected review | workflow/SBOM/artifact review |
| Cloud storage/network | Misconfiguration (Information disclosure): public bucket/DB or broad security group | data exfiltration / M / High | private-by-default, encryption, policy-as-code, no public database routes | IaC scan and reachability review |
| Cloud credentials | Attacker (Spoofing/Elevation): stolen long-lived key or broad role | account takeover / M / High | OIDC, short-lived least-privilege role, MFA/break-glass audit | IAM trust policy review |
| Recovery controls/backups | Insider/ransomware (DoS/Tampering): unauthorized restore/deletion or stale rollback | prolonged outage/data loss / M / High | dual approval, immutable backup retention, scoped restore role, integrity/reconciliation | isolated restore and denied-run tests |
| Simulated payment | Malicious caller (Tampering): submits card fields or manipulates payment outcome | sensitive data/commercial risk / L / High | accept synthetic scenario/token only, reject card fields, never contact processor | schema and security tests |
| Queue/worker | Malicious or faulty publisher (DoS/Tampering): poison/replay event | duplicate order/notification / M / Medium | signed/validated schema, idempotency, DLQ, rate/size limits | replay/poison tests |
| Telemetry | Operator or attacker (Disclosure/DoS): secret/high-cardinality data log flood | privacy/cost / M / Medium | redact, bounded labels, retention/quota controls | log inspection/cost tests |

Trust boundaries: browser to edge; edge to API; API to private persistence/messaging; CI to cloud via OIDC; operator to recovery control plane. Reassess after authentication, payment simulation, cloud IAM, or deployment decisions. Risks not yet mitigated in code remain open. Validation: team tabletop plus targeted tests and scans.
