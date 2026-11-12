# Phase 1 handoff to Phase 2

**Purpose/scope:** resume implementation without recreating validated foundations. Phase 1 is **PARTIALLY COMPLETE**; follow remaining issues in the [completion report](PHASE_1_COMPLETION_REPORT.md).

## Verified foundations

- Web project: `apps/web`; package lockfile is present in the working tree, Node 24/npm 11; `npm ci`, lint, typecheck, test, build and npm audit pass locally.
- API project: `apps/api`; Java 25, Spring Boot 4.1.2, Maven 3.9.11; `mvn -B -ntp -f apps/api/pom.xml verify` passes (2 tests).
- Actual local API endpoints: `GET /api/v1/health`, `/api/v1/info`; Actuator health at `/actuator/health`.
- Compose file validates but has not started: Docker Desktop daemon must be available. PostgreSQL/Redis are not yet application-wired.
- Requirements, architecture, security, ADR, FinOps, testing and team references live under `docs/`; no license or cloud resources exist.

## Phase 2 recommended order

1. Refresh Git status and review branch diff; run CI locally and on an authorized PR. Do not overwrite user changes.
2. Start Docker Desktop; validate Compose build, networking, loopback binding and health; fix any image tag/platform issues.
3. Team review requirements and approve unresolved decisions: identity/auth, PII retention, order states, inventory reservation, payment simulation, workload and recovery objectives.
4. Add Flyway or Liquibase only with schema design approval; wire PostgreSQL, migrations and Testcontainers. Keep Redis cache optional/rebuildable.
5. Implement modules incrementally: catalog/inventory → authentication/authorization → cart → idempotent order transaction → synthetic payment → tracking/admin.
6. Update acceptance/traceability and add API contracts, negative authorization tests, component/E2E tests, accessibility checks and dependency scanning.
7. Only after written cloud approval, budget estimate, region/owner/cleanup plan and security design, produce Terraform; run format/validate/plan/policy reviews. Never apply automatically.
8. Add OIDC identity after approved repo/environment trust restrictions; avoid static cloud keys. Introduce telemetry and recovery exercises only with access, evidence, cost and rollback controls.

## Commands

```powershell
cd apps/web
npm ci
npm run lint
npm run typecheck
npm test
npm run build

# From repository root, with Maven installed and JDK 25 selected:
mvn -B -ntp -f apps/api/pom.xml verify
docker compose config
docker compose up --build
```

After Docker is available, record health and integration results separately. Preserve named volume data unless an intentional local reset has been reviewed.
