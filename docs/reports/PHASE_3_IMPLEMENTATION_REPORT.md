# Phase 3 implementation report

**Status: PASS for locally verified Phase 3 scope; hosted Phase 3 CI is PENDING.**
**Branch:** `feature/phase-3-catalog-inventory` (based on Phase 2 commit `14a4dec`).
**Git:** Phase 2 commit `14a4dec` was explicitly pushed directly to `main`; Phase 3 remains uncommitted and unpushed. No Phase 3 PR exists and no Phase 3 commit/push was made.

## 1. Executive summary

Phase 3 adds a database-backed catalog and concurrency-safe inventory foundation to the existing session-authenticated modular monolith. Flyway V2 creates catalog, stock, reservation, and movement tables without altering V1. Public reads show active products only and use database pagination/search. Administrator writes are protected by backend `ROLE_ADMIN` and existing CSRF protection. Inventory changes lock the product inventory row in PostgreSQL and atomically update stock/reservation state with append-only movement history. The synchronized final-unit Testcontainers test confirms exactly one concurrent reservation succeeds.

## 2. Architecture and domain decisions

- Flat categories; products have DRAFT/ACTIVE/ARCHIVED lifecycle and are archived rather than deleted.
- USD-only prices use Java `BigDecimal` and PostgreSQL `NUMERIC(12,2)`; no FX behavior.
- SKU is normalized to uppercase; slug is lowercase and unique. Optional image URLs must be HTTPS; the server does not fetch them.
- PostgreSQL is authoritative. Catalog uses bound SQL parameters, allowlisted sorting and bounded database pagination; no external search or cache.
- Inventory availability is derived as `quantity_on_hand - quantity_reserved`.
- Same-product inventory mutations use `SELECT ... FOR UPDATE` within transactions. Reservation, release, inventory state and movement append commit or roll back together.
- Reservations/releases are ADMIN-only in Phase 3; customer cart/order flows are deferred.

Decisions are documented in ADR-016 (catalog/currency), ADR-017 (row locking), ADR-018 (ledger/reservations), and [catalog/inventory design](../03-architecture/CATALOG_INVENTORY_DESIGN.md).

## 3. Database schema and migration

`V2__create_catalog_inventory_schema.sql` creates `categories`, `products`, `inventory`, `inventory_reservations`, and `inventory_movements`. It defines unique keys, FKs, checks for price/currency/lifecycle/quantity invariants, public-query indexes, reservation/history indexes, and a PostgreSQL trigger rejecting movement updates/deletes. V1 remains unchanged. Fresh Testcontainers databases migrated V1 and V2 successfully; Compose PostgreSQL reports Flyway version 2 and Hibernate schema validation succeeds.

## 4. API and authorization

Public API: `GET /api/v1/categories[/{id}]`, `GET /api/v1/products` with category/search/sort/page parameters, `GET /api/v1/products/{id}`, and `GET /api/v1/products/{id}/availability`. Public reads exclude drafts, archived products and inactive categories. Page sizes are 1–100; sort keys are allowlisted and ID is a stable tie-breaker.

ADMIN + CSRF writes: category create/update; product create/update/status; one-time inventory initialization; signed stock adjustments; reservation and release. Admin inventory reads include paginated stock and product movement history. Requests use validation DTOs and responses do not expose JPA entities. Errors use the existing generic API envelope; conflicts cover uniqueness, unsupported lifecycle changes, duplicate release and insufficient stock.

Security integration tests cover anonymous admin rejection, CUSTOMER write denial and ADMIN product creation. Frontend role checks are supplementary; backend authorization is the enforcement boundary.

## 5. Inventory correctness

Inventory stores nonnegative on-hand/reserved counts, with reserved never exceeding on-hand. Each adjustment/reservation/release appends an actor-attributed movement with signed deltas and before/after snapshots. Corrections are new movements. A deterministic barrier-synchronized PostgreSQL test raced two attempts for one final unit: exactly one succeeded, one got HTTP-domain conflict, availability stayed zero (not negative), and one reservation/movement was persisted. Lifecycle tests cover adjustment, reservation, one-time release, insufficient quantity, duplicate release, and rollback after a movement FK failure.

## 6. Frontend functionality

The public catalog consumes the API for product cards/detail, category filtering, search, pagination, price and real stock availability; loading, empty and error states are provided. ADMIN pages support category create/update, product create/update/activate/archive, stock initialization/adjustment, reservation/release, and movement history. No products or inventory are seeded; empty state is genuine. The existing sign-in/session/CSRF implementation remains intact.

## 7. Verification evidence

| Check | Result |
|---|---|
| Maven `verify -DrunIntegrationTests=true` with PostgreSQL Testcontainers | **PASS:** 14 API tests, 0 failures/errors/skips. Includes migration, auth, catalog filters/pagination, role authorization, inventory lifecycle/rollback and final-unit concurrency. |
| Web lint and typecheck | **PASS** |
| Web Vitest | **PASS:** 15 tests across 4 files, including catalog results/filter/pagination/empty/error states. |
| Web production build | **PASS** |
| `npm audit --audit-level=moderate` | **PASS:** 0 vulnerabilities. |
| Gitleaks local working-tree scan | **PASS:** 834,179 bytes scanned, no leaks found. |
| Docker Compose | **PASS:** API, PostgreSQL, Redis and web healthy; Flyway V2 applied; Hibernate validates schema. Existing named volumes preserved. |
| HTTP smoke | **PASS:** empty public catalog and search return valid pages; invalid sort returns 400; anonymous admin read returns 401. No local admin/demo product was created. |
| Hosted GitHub CI | **PARTIAL:** Phase 2 commit `14a4dec` CI run `43243992683` succeeded. Phase 3 is not pushed, so no hosted Phase 3 run exists. |
| Maven OSV/dependency vulnerability scan | **PARTIAL:** configured in CI; not independently run for unpushed Phase 3 changes. No new Maven dependencies were added. |

Testcontainers ran from a Maven container with the Docker socket mounted and `TESTCONTAINERS_RYUK_DISABLED=true`; the tests themselves stopped their containers. The normal Compose Maven image build compiles/tests non-Docker tests, while Docker-backed integration evidence is from the explicit Testcontainers run.

## 8. Files created/modified for Phase 3

- `apps/api/src/main/resources/db/migration/V2__create_catalog_inventory_schema.sql`
- `apps/api/src/main/java/com/cloudresilience/modules/catalog/api/CatalogController.java`
- `apps/api/src/main/java/com/cloudresilience/modules/inventory/api/InventoryController.java`
- `apps/api/src/main/java/com/cloudresilience/modules/inventory/application/InventoryService.java`
- `apps/api/src/test/java/com/cloudresilience/modules/inventory/InventoryConcurrencyIntegrationTest.java`
- `apps/web/src/catalog.tsx`, `apps/web/src/catalog.test.tsx`; updates to `App.tsx`, `api.ts`, `styles.css`
- `docs/03-architecture/CATALOG_INVENTORY_DESIGN.md`, ADR-016/017/018, API endpoint reference, database and ER docs, README, local development guide.
- Existing Phase 2 `SecurityConfiguration` and `ApiExceptionHandler` extended to permit public catalog reads and map domain status errors.

## 9. Known issues and deferred work

- No administrator provisioning workflow or default admin exists. Admin UI requires a deliberately provisioned ADMIN identity.
- Sessions are process-local and do not survive API restart; multi-instance session storage remains a separate Spring Session/Redis milestone.
- Reservation HTTP endpoints are ADMIN-only until cart/order ownership and lifecycle are implemented.
- Search is substring `LIKE` over name/SKU; consider trigram/full-text indexes only if measured volume warrants them.
- No cloud deployment, cart, order, payment, caching, Kubernetes, or external search service is included.
- Phase 3 changes are uncommitted/unpushed; hosted Phase 3 CI and its Maven vulnerability scan remain pending.

## 10. Local execution

```powershell
Copy-Item .env.example .env   # if needed
docker compose up --build
cd apps/web; npm ci; npm run lint; npm run typecheck; npm test; npm run build; npm audit --audit-level=moderate
mvn -f apps/api/pom.xml verify -DrunIntegrationTests=true
```

For the tested Windows Docker Desktop Maven-container method, mount the repository and `/var/run/docker.sock`, set `TESTCONTAINERS_RYUK_DISABLED=true` and `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`, then run `mvn -f apps/api/pom.xml -DrunIntegrationTests=true verify`. Do not use `docker compose down -v` unless intentionally deleting named local database/cache data.

## 11. Next-phase readiness

The catalog/inventory foundation is ready for a later cart/order service to reserve stock through an internal transactional application boundary. Before Phase 4, define order ownership/idempotency, reservation expiry/consumption, customer-facing reservation authorization, and distributed session storage. No cloud resources were provisioned. No Phase 3 commit, push, merge, or PR was made.
