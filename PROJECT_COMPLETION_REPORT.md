# CloudResilience engineering handoff

**Status: NOT COMPLETE.** This report records delivered capabilities and remaining release gates without treating design work as proof. Hosted run `44309215656` on `977abdf68d116c62c156d262be9f49e0552a5ce6` passed all six CI jobs, including the three browser journeys, the PostgreSQL order/payment restore, all five asserted web security headers and the OWASP ZAP passive baseline (0 FAIL, 62 PASS, 6 WARN). The ZAP HTML/JSON reports were not retained because the container user could not write to the mounted directory; the workflow now makes that directory writable. Remaining open gates are listed below; no cloud deployment or production certification is claimed.

## Objectives and architecture

CloudResilience is a modular Java/Spring Boot and React/TypeScript e-commerce application. PostgreSQL is the durable business source of truth; Redis backs shared HTTP sessions. Checkout and inventory reservation remain transactional. RabbitMQ carries versioned order events from a PostgreSQL transactional outbox, with at-least-once delivery and idempotent notification/activity consumers. Local observability uses OpenTelemetry, Prometheus, Grafana, Loki and Tempo. AWS is represented by validated Terraform; no cloud resources were provisioned. Azure and Google Cloud are documented portability comparisons only.

The GitHub repository has a descriptive project summary and technology/cloud engineering topics, a single `main` branch, no open pull requests, a CI status badge, and linked phase/evidence documentation. Repository visibility was left unchanged.

## Phase status

| Phase | State | Evidence and open work |
|---|---|---|
| 1–5 | Implemented | See phase reports, architecture and CI history. |
| 6 | Implemented and integrated | Transactional outbox, RabbitMQ, retries/DLQ, inbox deduplication; report records 64 passing integration tests. |
| 7 | Implemented locally | Observability stack, dashboards, alerts and a catalog-only load test; no cloud-scale capacity claim. |
| 8 | Terraform implemented and validated | Hosted validation includes fmt, backend-free init, validate, TFLint and Checkov. No apply or cloud deployment. |
| 9 | Partial | Hosted run `44265982978` passed restoration of 1 order, 1 item, 1 payment, 1 reservation and event/inbox state into a fresh isolated DB; 5 Flyway migrations and API smoke passed; reported integrity mismatches were zero. Archive SHA-256 and counts are in the DR report. Procedure duration was 11 seconds, not RTO; RPO remains unmeasured. Redis loss, RabbitMQ outage/replay and cloud failover are not run. |
| 10 | Partial | Hosted run `44309215656` (`977abdf`) passed all six jobs: 3 browser journeys, order/payment restore, direct web-image build/start, all five security headers and the ZAP passive baseline. ZAP logged 0 FAIL, 62 PASS and 6 WARN; its HTML/JSON reports were not uploaded because the container user could not write to `/zap/wrk`, so the workflow now makes that mount writable. Cost totals remain not estimated; no approved region/workload inputs. |

## Delivered engineering decisions

- PostgreSQL remains authoritative for identity, catalog, inventory, carts, orders, payments, outbox and inbox state.
- Redis session loss requires re-authentication; it does not represent business-data loss.
- Checkout commits orders, reservations and domain events together. Event consumers operate downstream with idempotency.
- AWS Terraform is validated only. State bootstrap, plans requiring credentials, deployment, production operations and cloud recovery were not performed.
- The multi-cloud document maps service capabilities and semantics; it does not claim Azure/GCP deployment parity.
- FinOps comparison documents pricing sources and the required inputs. Monthly totals and savings remain **NOT ESTIMATED** because region, currency, SKUs, usage, retention and HA/RTO/RPO assumptions were not supplied or measured.

## Test and scan evidence

- Latest local frontend run: Vitest **21 passed across 6 files**; lint, typecheck and production build passed; Playwright discovered **3 tests**.
- Hosted run `44249204505` on `dd7560dd3854bc6675bbe0f8919af7c4d2774a56`: all six jobs passed, including browser journeys **3 passed / 0 skipped**.
- Hosted run `44253328788` on `fbe04ad6e2b61a1cb5bd6baa13d4e619a44a6c3f`: web, API, Terraform/IaC, Gitleaks and OSV jobs passed; Browser end-to-end failed during combined Compose startup.
- Hosted runs `44255857779` (`18042b6`) and `44264241686` (`82cb5c7`): Browser E2E (3 journeys) and PostgreSQL order/payment recovery passed; web startup/header steps failed before DAST.
- Hosted run `44265982978` (`05295f2`): E2E, order/payment restore, direct web-image build/start, and all non-browser jobs passed. The response-header assertion failed, so ZAP was skipped. Its recovery artifact was downloaded and inspected; exact evidence is in `docs/07-disaster-recovery/DR_DRILL_RESULTS.md`.
- Hosted run `44278969578` (`3a92fed`) saved a response diagnostic showing the web container had exited when `docker exec` ran. The follow-up explicitly runs Nginx in foreground and captures `docker inspect`/container logs before the header probe.
- Hosted run `44282141760` (`fdf151e`) captured Nginx's exact error: `open() "/tmp/nginx/nginx.pid" failed (13: Permission denied)`. The fix sets the PID path to `/var/cache/nginx/nginx.pid` and removes the root-owned PID file created by the build-time `nginx -t`. A local image build then started Nginx and returned the asserted CSP, Referrer-Policy, X-Content-Type-Options, X-Frame-Options and Permissions-Policy headers; run `44309215656` confirmed this on the hosted runner.
- Hosted run `44309215656` on `977abdf68d116c62c156d262be9f49e0552a5ce6`: **all six jobs passed**. Browser journeys **3/3**, order/payment restore and API smoke passed, and the response-header probe returned CSP, Referrer-Policy, X-Content-Type-Options, X-Frame-Options and Permissions-Policy (artifact `web-response-headers-44309215656`). ZAP passive baseline completed with **0 FAIL, 62 PASS, 6 WARN**. The ZAP report step logged `AccessDeniedException /zap/wrk/zap-baseline.html`, so the artifact contained no report; the workflow now makes the mounted results directory writable before the scan.
- A high-severity development-toolchain advisory, GHSA-fc5q-39xc-vjpf (picomatch), remains open. Production dependency audit passing does not resolve this advisory.
- ZAP baseline is passive scanning evidence only; it is not a penetration test or a clean bill of health. Run `44309215656` recorded **6 warnings** (suspicious comments, Server version disclosure, storable/cacheable content, CSP wildcard directives, modern web application fingerprinting, missing Cross-Origin-Embedder-Policy) alongside 62 passing checks and 0 failures. No external penetration test or formal audit was performed.

## Recovery and cloud evidence

Local recovery artifacts are kept outside the repository. The hosted synthetic CI drill backed up and restored 1 order, 1 item, 1 payment, 1 reservation, 1 inventory product/row, 2 movements and 1 outbox event to a fresh isolated database. It verified 5 Flyway migration records, two consumer inbox records, matching snapshots, zero consistency mismatches and recovered API readiness/catalog access. The archive was 73,893 bytes with SHA-256 `ab7b5224aaa301f017cea6100dc8e486e219d0f69bee75760ef83068ecac2003`. The 11-second procedure interval is not RTO; RPO was not measured.

No AWS/Azure/GCP resources, database, DNS changes, production deployment, cross-region replication, or cloud recovery drill exist. Terraform was not applied. No real payments or provider charges occurred.

## Current handoff and next gates

1. Re-run main CI after the ZAP report-permission fix and confirm the HTML/JSON report artifacts upload. No other release-gate step is currently failing.
2. Review the uploaded ZAP report and record counts/checksums without presenting the CI exercise as cloud RTO/RPO.
3. Resolve or formally track the high-severity picomatch advisory; do not suppress it.
4. Obtain region, currency, workload, SKU, HA, backup-retention and recovery-target assumptions before publishing cost totals.
5. If later authorized, run cloud deployment and recovery only under a separate explicit deployment approval; nothing in this work provisioned resources.

## Documentation index

- [README](README.md)
- [Final system architecture](docs/03-architecture/FINAL_SYSTEM_ARCHITECTURE.md)
- [Event-driven architecture](docs/03-architecture/EVENT_DRIVEN_ARCHITECTURE.md)
- [Observability architecture](docs/03-architecture/OBSERVABILITY_ARCHITECTURE.md)
- [AWS deployment guide](docs/10-deployment/AWS_DEPLOYMENT_GUIDE.md)
- [Final deployment guide](docs/10-deployment/FINAL_DEPLOYMENT_GUIDE.md)
- [Disaster recovery drill results](docs/07-disaster-recovery/DR_DRILL_RESULTS.md)
- [Final recovery summary](docs/12-disaster-recovery/FINAL_RECOVERY_SUMMARY.md)
- [Restore runbook](docs/07-disaster-recovery/RESTORE_RUNBOOK.md)
- [Multi-cloud portability](docs/03-architecture/MULTI_CLOUD_PORTABILITY.md)
- [Multi-cloud cost comparison](docs/08-finops/MULTI_CLOUD_COST_COMPARISON.md)
- [Final security review](docs/05-security/FINAL_SECURITY_REVIEW.md)
- [Final validation matrix](docs/09-testing/FINAL_VALIDATION_MATRIX.md)
- [Phase 9 report](docs/reports/PHASE_9_IMPLEMENTATION_REPORT.md)
- [Phase 10 report](docs/reports/PHASE_10_IMPLEMENTATION_REPORT.md)
