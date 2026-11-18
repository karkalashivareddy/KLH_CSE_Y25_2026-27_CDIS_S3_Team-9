# Final security review

**Status:** source and CI controls reviewed on 2026-11-18. This is a repository-level engineering review, not a penetration test, cloud audit, or certification. Phase 10 closure remains pending final test evidence.

## Implemented controls evidenced in source

- The application uses Spring Security, password hashing, authorization rules, CSRF-protected browser sessions and Redis-backed distributed HTTP sessions; consult [authentication and sessions](AUTHENTICATION_AND_SESSIONS.md) and the Phase 2–5 reports for tested scope.
- PostgreSQL is the durable source for customer/business state. Checkout/inventory writes are transactionally handled; outbox publication is at-least-once and consumers use deduplication (Phase 6 report).
- CI on `main` runs Gitleaks, OSV Maven scan, a production dependency audit, backend/frontend checks, container builds and HIGH/CRITICAL Trivy scans. Terraform CI runs fmt, backend-free init, validate, TFLint and Checkov.
- AWS Terraform defaults to private data subnets, restrictive security groups, encrypted storage, Secrets Manager references, KMS, task/execution role separation, immutable ECR tags, TLS ALB, WAF, bounded log retention, and deletion/backup controls. Terraform is validated; AWS resource behavior is not provisioned or audited.
- Application container images use non-root runtime users. Nginx runs unprivileged in the web container.

## Security checks and limits

| Area | Evidence/status | Remaining validation |
|---|---|---|
| Authentication, sessions, CSRF, authorization | Implemented and covered by existing API/frontend suites; see phase reports | Complete negative IDOR/role matrix and run on final main SHA. |
| Secrets and repository history | Gitleaks CI passed run 44234484421 on `fab9c13` | Continue scanning every push; no credentialed cloud secret rotation was tested. |
| Maven dependencies | OSV CI passed run 44234484421 on `fab9c13` | Re-run after any dependency change. |
| npm production packages | `npm audit --omit=dev --audit-level=moderate` passed locally and in CI run 44234484421 on `fab9c13`; Vite is dev-only because production serves static files from Nginx | Re-run on every change. |
| npm development toolchain | HIGH [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf) affects picomatch through 4.0.7; GitHub Advisory lists no patched version. Vite dev server binds to loopback. | Open security finding; monitor upstream release and restore full audit gate after a patched version is available. Phase 10 security acceptance is not met. |
| Container OS and libraries | Trivy scans both built images at HIGH/CRITICAL; CI run 44234484421 passed on `fab9c13` | Re-scan after image changes and record findings/counts. |
| Terraform | TFLint and Checkov required; Phase 8 final CI pass reported by run 44108903172 at commit 1fe7b13 | Re-run after subsequent Terraform changes. No provider-authenticated plan. |
| DAST / OWASP ZAP | NOT RUN in the verified workflow | Add a local disposable Compose target and execute baseline scan before claiming DAST. |
| Cloud IAM/network | Terraform source reviewed; no AWS account/role exists in evidence | No IAM Access Analyzer, live SG review, secret rotation, TLS endpoint or AWS Config findings. |
| Backups and recovery access | Local empty-state backup/checksum/isolated restore plus API readiness smoke passed; business data absent | Verify populated order/inventory restoration, operator separation and broker/session recovery. |
| Telemetry privacy | Structured telemetry documented/implemented in Phase 7 | Inspect actual traces/logs after final changes; ensure credentials, cookies, session IDs and customer data are absent. |

## Required release gate

Before marking the final project secure, record the exact commit and results for Gitleaks, OSV, npm audit, Trivy, Terraform scanners, API/frontend integration, E2E journeys, and a local DAST scan. Review each finding; document exceptions with owner, reason, compensating control and review date. Do not treat a clean automated scan as a penetration test.

## Not claimed

- No third-party penetration test or formal compliance audit was performed.
- No AWS deployment, live IAM review, cloud security configuration evaluation, or production secret rotation took place.
- No conclusion about zero vulnerabilities is made; scanner results are time- and database-dependent.
