# CloudResilience

**A secure, cost-conscious e-commerce platform and cloud resilience engineering project.**

Course project for Cloud Infrastructure and Services (25CS2206E), Trimester 5, Team 9, academic year 2026–2027.

> **Status: Phase 3 is on `main`; Phase 4 distributed sessions and customer carts are in implementation on `feature/phase-4-distributed-state-cart`.** Phase 3 commit `aa9945c` was pushed directly to `main` and hosted CI passed. Phase 4 verification is recorded in its report; Docker-dependent gates may be blocked by local runtime availability. No cloud deployment or production e-commerce operations are claimed.

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
| Customer shopping cart | Phase 4: PostgreSQL persistence and customer-owned API/UI; no reservation or checkout |
| AWS/Azure/GCP provisioning | Not started; no resources authorized or deployed |
| Cart, orders, payments, recovery automation | Planned; not implemented |

See [Phase 1 closure](docs/reports/PHASE_1_CLOSURE_VERIFICATION.md), [Phase 2 implementation report](docs/reports/PHASE_2_IMPLEMENTATION_REPORT.md), [Phase 3 implementation report](docs/reports/PHASE_3_IMPLEMENTATION_REPORT.md), [Phase 4 implementation report](docs/reports/PHASE_4_IMPLEMENTATION_REPORT.md), [catalog/inventory design](docs/03-architecture/CATALOG_INVENTORY_DESIGN.md), and [authentication/session security](docs/05-security/AUTHENTICATION_AND_SESSIONS.md) for evidence and scope.

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
infrastructure/           Future cloud/IaC design and implementations
platform/                 Local/container/observability tooling
docs/                     Product, architecture, security, testing, operations
tests/                    Cross-application tests (as introduced)
.github/workflows/        CI checks; no cloud credentials
```

## Documentation

- [Project charter](docs/01-project-overview/PROJECT_CHARTER.md)
- [Requirements](docs/02-requirements/REQUIREMENTS_SPECIFICATION.md)
- [System architecture](docs/03-architecture/SYSTEM_ARCHITECTURE.md)
- [Architecture decisions](docs/04-architecture-decisions/)
- [Security and threat model](docs/05-security/SECURITY_ARCHITECTURE.md)
- [Authentication and sessions](docs/05-security/AUTHENTICATION_AND_SESSIONS.md)
- [Identity schema](docs/03-architecture/IDENTITY_SCHEMA.md)
- [Catalog and inventory design](docs/03-architecture/CATALOG_INVENTORY_DESIGN.md)
- [Distributed sessions and cart design](docs/03-architecture/DISTRIBUTED_STATE_CART_DESIGN.md)
- [API endpoint reference](docs/10-deployment/API_ENDPOINTS.md)
- [Testing strategy](docs/09-testing/TEST_STRATEGY.md)
- [Toolchain versions](docs/10-deployment/TOOLCHAIN_VERSIONS.md)
- [Team workflow](docs/11-team-workflow/BRANCHING_STRATEGY.md)

## Team

| Roll number | Name | Initial focus |
|---|---|---|
| 2520030105 | Karkala Shiva Reddy | Architecture, cloud, Terraform, DevSecOps, CI/CD, resilience, integration |
| 2520030203 | Harivikash Katta | Backend, APIs, data, distributed systems, backend testing |
| 2520030286 | Kari Prudhi Sai | Frontend, dashboard, observability UI, E2E, documentation support |

Responsibilities are collaborative; see [team responsibilities](docs/11-team-workflow/TEAM_RESPONSIBILITIES.md).

## Engineering and security

Use feature branches and pull requests. Never commit credentials. `.env` is ignored; `.env.example` contains local-only defaults and is not suitable for deployment. No cloud resources, paid services, credentials, or deployment have been created. See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [cost policy](docs/08-finops/COST_MANAGEMENT_STRATEGY.md).

## License

No license is declared. All rights remain with their respective owners; do not assume reuse rights. A license should be selected by the repository maintainers before one is added.
