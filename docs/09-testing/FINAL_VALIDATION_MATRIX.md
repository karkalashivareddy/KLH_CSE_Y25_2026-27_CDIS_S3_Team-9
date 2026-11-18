# Final validation matrix

**Status:** interim evidence snapshot for Phase 10. Update only from exact local or hosted execution output and identify its commit SHA.

| Area | Required evidence | Current status | Notes |
|---|---|---|---|
| Phase 6 backend and RabbitMQ integration | Full Maven/Testcontainers suite, zero skips, broker outage/retry/DLQ/inbox scenarios | PASS per Phase 6 implementation report | Report records 64 tests, 64 passed, no skips, with Docker-backed PostgreSQL/RabbitMQ. Reconfirm on final SHA. |
| Phase 7 backend | Maven full suite | PASS per Phase 7 report; latest local rerun had 36 Docker-dependent skips; earlier full run and hosted exact report commit passed | Distinguish the skipped local rerun from the earlier/hosted full suite. |
| Phase 7 frontend | Vitest/lint/typecheck/build | PASS per Phase 7 report | Re-run at final commit if frontend changes. |
| Phase 7 load test | k6 five-VU catalog browse, 30 seconds | PASS: 150 requests, 0% failures, p50 8.6669 ms, p95 21.1790 ms, p99 29.3967 ms | One local catalog-only run; no checkout, infrastructure saturation or cloud performance conclusion. |
| Phase 8 Terraform | fmt, backend-free init, validate, TFLint, Checkov | PASS on CI run 44108903172, commit `1fe7b1308958b974758b069abbced544e48ac5c3` | No credentialed plan or apply. Any subsequent changes require a new scan. |
| Phase 8 API integration and container | Maven verify with integrations; build and Trivy API image | PASS on CI run 44108903172, exact SHA above | Counts/findings should be taken from job details before final aggregate report. |
| Phase 8 frontend and container | npm audit/lint/typecheck/tests/build; build and Trivy web image | PASS on CI run 44108903172, exact SHA above | Phase 8 report records local frontend 21 tests across 6 files. |
| Phase 8 secret/dependency scan | Gitleaks, OSV | PASS on CI run 44108903172, exact SHA above | No claim beyond scanned commit/dependency graph. |
| Current integrated CI | Backend/integration, frontend, Terraform/IaC, secret/dependency/container scans | PASS on run [44245526649](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44245526649), exact SHA `e47724aa1aca6319bdb347c689e57c7472b45cd5` | This verifies the current implementation commit; remaining Phase 9/10 acceptance work is still open. |
| Docker Compose config | `docker compose config --quiet` | PASS locally during Phase 8 | Emits a local Docker config access warning; daemon unavailable later for image/Testcontainers runs. |
| Backup/restore drill | Backup, checksum, isolated restore, schema and data consistency, measured RTO/RPO | PARTIAL PASS | Empty-state and populated synthetic catalog/inventory restore passed locally; 5 Flyway records, zero inventory ledger mismatches, and recovered-DB API readiness/catalog smoke passed. No order/payment records or measured RTO/RPO; see Phase 9 drill evidence. |
| E2E shopper and checkout success journey | Browser run for registration, sign-in, catalog, cart, simulated payment and order details | PASS locally (2/2) | Playwright 1.65.1, local Compose API, synthetic stock fixture. Chromium binary 1248 from local cache supplied via `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` because pinned browser download was blocked. Fixture seeder passed PowerShell parsing but its DB execution was NOT VERIFIED after Docker Desktop returned API 500. Not currently in hosted CI; failure journeys remain NOT RUN. |
| DAST | Local OWASP ZAP baseline or equivalent | NOT RUN | No DAST job in current workflow. |
| Azure/GCP adapters/deployments | Contract tests and provider deployment evidence | NOT IMPLEMENTED / NOT RUN | Portability is described in design documentation only. |
| Cloud provisioning and regional failover | Provider state and controlled exercise | NOT RUN / NOT AUTHORIZED | No real cloud resources were created. |

## Snapshot rule

This matrix is not a substitute for CI. Before final closure, add exact final main SHA, workflow URL, full test counts, integration skip count, npm/Maven/IaC/container scan summaries, E2E and DAST outcomes, and recovery drill records. Use `BLOCKED`, `SKIPPED`, `NOT RUN`, or `NOT VERIFIED` where evidence is absent; never convert these to PASS.
