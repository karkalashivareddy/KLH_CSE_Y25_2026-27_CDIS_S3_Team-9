# CloudResilience

**A secure, cost-conscious e-commerce platform and cloud resilience engineering project.**

Course project for Cloud Infrastructure and Services (25CS2206E), Trimester 5, Team 9, academic year 2026–2027.

> **Status: Phase 1 foundations in progress.** This repository is being established on a feature branch. Architecture and cloud deployments described as target/future are not deployed. No live cloud telemetry or production e-commerce operations are claimed.

## Product and target architecture

CloudResilience is intended to demonstrate product foundations and cloud engineering through a modular Java/Spring API, React/TypeScript web app, PostgreSQL, Redis, containers, infrastructure as code, observability, and tested recovery workflows. AWS is the proposed primary cloud; Azure and GCP are comparison/portability targets. The initial implementation is deliberately a modular monolith with future independently deployable workers—not a collection of premature microservices.

## Current implementation

| Capability | State |
|---|---|
| Repository audit and engineering foundations | In progress |
| Web UI foundation | In progress |
| API health and metadata foundation | In progress |
| Local PostgreSQL/Redis Compose environment | Planned until validated |
| AWS/Azure/GCP provisioning | Not started; no resources authorized or deployed |
| Store, payments, administration, recovery automation | Planned; not implemented |

See [Phase 1 report](docs/reports/PHASE_1_COMPLETION_REPORT.md) for verified status and [handoff](docs/reports/PHASE_1_HANDOFF.md) for next steps.

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

Use feature branches and pull requests. Never commit credentials. `.env` is ignored; `.env.example` contains non-secret local defaults only. No cloud resource creation, paid service, GitHub settings change, push, or merge is part of Phase 1. See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [cost policy](docs/08-finops/COST_MANAGEMENT_STRATEGY.md).

## License

No license is declared. All rights remain with their respective owners; do not assume reuse rights. A license should be selected by the repository maintainers before one is added.
