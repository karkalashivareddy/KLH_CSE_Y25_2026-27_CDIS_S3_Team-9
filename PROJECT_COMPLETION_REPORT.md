# CloudResilience engineering handoff

**Status: NOT COMPLETE.** This report records delivered capabilities and remaining release gates without treating design work as proof. Latest verified workflow commit `3a92fed0a7a029520f51d463c34d4b323600521a` is on the single `main` branch; run `44278969578` passed browser and recovery steps but failed before DAST because the Nginx container had exited. The current follow-up starts Nginx directly in foreground and retains container diagnostics; its hosted result is pending.

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
| 10 | Partial | Hosted run `44278969578` passed all 3 browser journeys and order/payment restore. The deployable Nginx process exited before header/ZAP validation. The latest follow-up starts Nginx directly in foreground and captures container logs. Cost totals remain not estimated; no approved region/workload inputs. |

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
- A high-severity development-toolchain advisory, GHSA-fc5q-39xc-vjpf (picomatch), remains open. Production dependency audit passing does not resolve this advisory.
- ZAP baseline, if successful in the pending run, is passive scanning evidence only; it is not a penetration test. No external penetration test or formal audit was performed.

## Recovery and cloud evidence

Local recovery artifacts are kept outside the repository. The hosted synthetic CI drill backed up and restored 1 order, 1 item, 1 payment, 1 reservation, 1 inventory product/row, 2 movements and 1 outbox event to a fresh isolated database. It verified 5 Flyway migration records, two consumer inbox records, matching snapshots, zero consistency mismatches and recovered API readiness/catalog access. The archive was 73,893 bytes with SHA-256 `ab7b5224aaa301f017cea6100dc8e486e219d0f69bee75760ef83068ecac2003`. The 11-second procedure interval is not RTO; RPO was not measured.

No AWS/Azure/GCP resources, database, DNS changes, production deployment, cross-region replication, or cloud recovery drill exist. Terraform was not applied. No real payments or provider charges occurred.

## Current handoff and next gates

1. Verify the current main CI run after the Docker-runner follow-up; ensure all E2E, recovery, image, header and ZAP steps execute.
2. Record the order/payment restore manifest and ZAP findings from CI artifacts; document counts/checksum without presenting the CI exercise as cloud RTO/RPO.
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
