# CloudResilience

<p align="center"><img src="docs/assets/cloudresilience-architecture.gif" alt="Animated CloudResilience architecture: React, Spring Boot, PostgreSQL outbox, RabbitMQ consumers and Redis sessions" width="100%"></p>

**A secure, cost-conscious e-commerce platform and cloud resilience engineering project.**

[![CI](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/workflows/ci.yml)

![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white) ![React 19](https://img.shields.io/badge/React-19-149ECA?logo=react&logoColor=white) ![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white) ![Terraform](https://img.shields.io/badge/Terraform-validated-7B42BC?logo=terraform&logoColor=white)

Course project for Cloud Infrastructure and Services (25CS2206E), Trimester 5, Team 9, academic year 2026–2027.

> **Status: Phases 6–8 are implemented; Phases 9–10 remain incomplete.** Hosted run `44265982978` passed all 3 browser journeys and an isolated order/payment restore: 1 order and payment were recovered with matching database snapshots and API smoke. The web image started, but the host-published header probe did not return a response, so ZAP did not run. The latest workflow probes headers inside the container and scans it on the isolated Docker network. Actual RTO/RPO, dependency failure drills, the high-severity development dependency advisory and final security validation remain open. No cloud deployment or production operation is claimed. See [project handoff](PROJECT_COMPLETION_REPORT.md).

## Project dashboard

| Area | State |
|---|---|
| Phases | 1–8 implemented; Phases 9–10 in progress |
| Architecture | Spring Boot modular monolith; React web app |
| Data and events | PostgreSQL source of truth; transactional outbox; RabbitMQ at-least-once delivery; Redis sessions only |
| Cloud | AWS Terraform implemented and statically validated; no cloud resources deployed |
| Recovery | CI restored a synthetic order/payment into an isolated database; actual RTO/RPO not measured |
| Open gates | Nginx runtime exits in current DAST job; high-severity dev dependency advisory open; monthly cloud estimates not available |

Latest verified hosted run [44278969578](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44278969578) passed the three browser journeys and PostgreSQL recovery drill; the web container exited before header/DAST checks. The follow-up starts Nginx in the foreground and captures runtime logs. For verified scope, see the [validation matrix](docs/09-testing/FINAL_VALIDATION_MATRIX.md) and [project handoff](PROJECT_COMPLETION_REPORT.md).

## Product and target architecture

CloudResilience is intended to demonstrate product foundations and cloud engineering through a modular Java/Spring API, React/TypeScript web app, PostgreSQL, Redis, containers, infrastructure as code, observability, and tested recovery workflows. AWS is the proposed primary cloud; Azure and GCP are comparison/portability targets. The initial implementation is deliberately a modular monolith with future independently deployable workers—not a collection of premature microservices.

## Current implementation

| Capability | State |
|---|---|
| Repository and Phase 1 engineering foundations | Implemented; hosted CI passed on `main` |
| Web foundation and customer authentication UI | Implemented locally; frontend checks pass |
| API health, registration, sessions and authorization | Implemented; unit and PostgreSQL Testcontainers integration tests pass locally |
| PostgreSQL schema and Flyway migration | Implemented and verified against PostgreSQL 17.7 locally |
| Product catalog and categories | Implemented in V2; public search/filter/pagination and admin management are API-backed |
| Inventory adjustment, reservation and movement ledger | Implemented with transactional PostgreSQL row locks; concurrent final-unit integration test passes |
| Redis integration | Phase 4: shared Spring Session backend; not a cart/cache store |
| Customer shopping cart | Phase 4 implementation present; PostgreSQL/Redis integration tests pass locally |
| Orders and checkout | Phase 5 implementation present: cart-asserted checkout, snapshots, atomic inventory reservation, customer idempotency, state history and order APIs/UI |
| Event-driven order processing | Phase 6 implementation present: transactional outbox, versioned events, RabbitMQ retry/DLQ topology, idempotent notification/activity consumers, operator replay APIs |
| Observability | Phase 7 local profile implemented: Prometheus, Grafana, Loki, Tempo, OpenTelemetry Collector and Alloy; metrics scrape and traces verified locally, and a bounded catalog load test completed |
| AWS infrastructure | Phase 8 Terraform source validated locally and by hosted CI; no AWS resources provisioned |
| Disaster recovery | Empty-state and synthetic catalog/inventory restores passed locally; hosted 1-order/1-payment isolated restore and API smoke passed in run 44265982978; RTO/RPO remain unmeasured |
| E2E test setup | Hosted Playwright registration/catalog/cart/checkout and insufficient-stock journeys passed 3/3 using the synthetic fixture. |
| Simulated payments | Deterministic simulator only; no real provider, payment data, or charges |
| Order and event runtime verification | Phase 6 regression suite passed 64 tests; Phase 7 full backend suite passed 66 tests, 0 skipped |
| AWS/Azure/GCP provisioning | Not started; no resources authorized or deployed |
| Automatic reservation expiry, remote payment provider and recovery automation | Not implemented; payment simulator only |

See [project handoff](PROJECT_COMPLETION_REPORT.md), [Phase 1 closure](docs/reports/PHASE_1_CLOSURE_VERIFICATION.md), [Phase 2 implementation report](docs/reports/PHASE_2_IMPLEMENTATION_REPORT.md), [Phase 3 implementation report](docs/reports/PHASE_3_IMPLEMENTATION_REPORT.md), [Phase 4 implementation report](docs/reports/PHASE_4_IMPLEMENTATION_REPORT.md), [Phase 5 implementation report](docs/reports/PHASE_5_IMPLEMENTATION_REPORT.md), [Phase 6 implementation report](docs/reports/PHASE_6_IMPLEMENTATION_REPORT.md), [Phase 8 implementation report](docs/reports/PHASE_8_IMPLEMENTATION_REPORT.md), [Phase 9 implementation report](docs/reports/PHASE_9_IMPLEMENTATION_REPORT.md), [Phase 10 implementation report](docs/reports/PHASE_10_IMPLEMENTATION_REPORT.md), [AWS deployment guide](docs/10-deployment/AWS_DEPLOYMENT_GUIDE.md), [event-driven architecture](docs/03-architecture/EVENT_DRIVEN_ARCHITECTURE.md), [DR restore runbook](docs/07-disaster-recovery/RESTORE_RUNBOOK.md), [multi-cloud architecture comparison](docs/03-architecture/MULTI_CLOUD_PORTABILITY.md), and [multi-cloud cost estimate worksheet](docs/08-finops/MULTI_CLOUD_COST_COMPARISON.md) for evidence and scope.

## Quick start

Prerequisites and exact commands are maintained in [Local development](docs/10-deployment/LOCAL_DEVELOPMENT.md). Frontend currently requires Node.js 24 LTS and npm. Backend requires Java 25 and Maven 3.9.9+ (Maven is not currently installed on the original inspection machine). Docker Desktop is needed for Compose and Testcontainers.

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Do not treat a command as verified unless the validation report records its result. A Compose setup is not a cloud deployment.

## Repository map

```text
apps/web/                 React + TypeScript frontend
apps/api/                 Spring Boot modular-monolith API
services/                 Future worker boundaries (not implemented)
packages/                 Future shared contracts (not implemented)
infrastructure/           Terraform modules and local recovery scripts
platform/                 Local/container/observability tooling
docs/                     Product, architecture, security, testing, operations
tests/                    Cross-application tests (as introduced)
.github/workflows/        CI checks; no cloud credentials
```

## Documentation

- [Project charter](docs/01-project-overview/PROJECT_CHARTER.md)
- [Requirements](docs/02-requirements/REQUIREMENTS_SPECIFICATION.md)
- [System architecture](docs/03-architecture/SYSTEM_ARCHITECTURE.md)
- [Final system architecture](docs/03-architecture/FINAL_SYSTEM_ARCHITECTURE.md)
- [Architecture decisions](docs/04-architecture-decisions/)
- [Security and threat model](docs/05-security/SECURITY_ARCHITECTURE.md)
- [Authentication and sessions](docs/05-security/AUTHENTICATION_AND_SESSIONS.md)
- [Identity schema](docs/03-architecture/IDENTITY_SCHEMA.md)
- [Catalog and inventory design](docs/03-architecture/CATALOG_INVENTORY_DESIGN.md)
- [Distributed sessions and cart design](docs/03-architecture/DISTRIBUTED_STATE_CART_DESIGN.md)
- [Order and checkout design](docs/03-architecture/ORDER_CHECKOUT_DESIGN.md)
- [Event-driven architecture](docs/03-architecture/EVENT_DRIVEN_ARCHITECTURE.md)
- [Event catalog](docs/03-architecture/EVENT_CATALOG.md)
- [Observability architecture](docs/03-architecture/OBSERVABILITY_ARCHITECTURE.md)
- [SLO/SLI definitions](docs/07-observability/SLO_SLI_DEFINITIONS.md)
- [Alerting runbook](docs/07-observability/ALERTING_RUNBOOK.md)
- [Dashboard catalog](docs/07-observability/DASHBOARD_CATALOG.md)
- [Load-test results](docs/07-observability/LOAD_TEST_RESULTS.md)
- [API endpoint reference](docs/10-deployment/API_ENDPOINTS.md)
- [Testing strategy](docs/09-testing/TEST_STRATEGY.md)
- [Toolchain versions](docs/10-deployment/TOOLCHAIN_VERSIONS.md)
- [Final deployment guide](docs/10-deployment/FINAL_DEPLOYMENT_GUIDE.md)
- [Final recovery summary](docs/12-disaster-recovery/FINAL_RECOVERY_SUMMARY.md)
- [Project completion handoff](PROJECT_COMPLETION_REPORT.md)
- [Team workflow](docs/11-team-workflow/BRANCHING_STRATEGY.md)

## Team

| Roll number | Name | Initial focus |
|---|---|---|
| 2520030105 | Karkala Shiva Reddy | Architecture, cloud, Terraform, DevSecOps, CI/CD, resilience, integration |
| 2520030203 | Harivikash Katta | Backend, APIs, data, distributed systems, backend testing |
| 2520030286 | Kari Prudhi Sai | Frontend, dashboard, observability UI, E2E, documentation support |

Responsibilities are collaborative; see [team responsibilities](docs/11-team-workflow/TEAM_RESPONSIBILITIES.md).

## Engineering and security

For this execution, work is integrated directly on `main` without pull requests, per repository-owner direction. Never commit credentials. `.env` is ignored; `.env.example` contains local-only defaults and is not suitable for deployment. No cloud resources, paid services, or production deployment have been created. See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [cost policy](docs/08-finops/COST_MANAGEMENT_STRATEGY.md).

## License

No license is declared. All rights remain with their respective owners; do not assume reuse rights. A license should be selected by the repository maintainers before one is added.
