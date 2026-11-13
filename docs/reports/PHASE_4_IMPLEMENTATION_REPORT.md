# Phase 4 implementation report

**Overall status: PARTIALLY IMPLEMENTED — Docker-dependent acceptance gates are BLOCKED.** Code, integration tests, Compose configuration and documentation are present, but PostgreSQL/Redis Testcontainers and runtime behavior could not be exercised because Docker Desktop's Linux engine is unavailable. Phase 4 is not considered complete until these gates pass.

**Branch:** `feature/phase-4-distributed-state-cart`  
**Base:** `main` at `aa9945c`  
**Commit/push/merge/PR:** none.  
**Hosted Phase 4 CI:** NOT RUN; branch is not pushed.

## Executive summary

Phase 4 adds shared Spring Session storage on Redis and a PostgreSQL-backed customer cart. PostgreSQL remains the durable source for identity, catalog, inventory and cart lines; Redis stores shared HTTP sessions only. Customer cart routes enforce `ROLE_CUSTOMER`, derive ownership from the authenticated principal, calculate current-price totals using `BigDecimal`, display current availability, and do not reserve inventory or create orders/payments.

Implemented code includes externalized Redis/session configuration, credential-stripped session principals, Redis-aware readiness/fail-closed behavior, Flyway V3, transactional cart operations with database constraints and aggregate row locking, customer cart UI, two-API Compose profile, ADRs, and Docker-backed integration tests. Integration test assertions are not verified on this machine.

## Compatibility and dependency decisions

Baseline inspected and retained: Spring Boot **4.1.2**, Java **25**, PostgreSQL **17.7-alpine3.22**, Redis **8.4.0-alpine3.22**, React/React DOM **19.3.1**, React Router **7.18.5**, Vite **8.3.8**, Node **24.19.0**, npm **11.17.0**, Maven **3.9.11** (local temp distribution).

Boot-managed versions resolved through Maven: `spring-boot-starter-data-redis` **4.1.2**; `spring-session-data-redis` **4.1.1**; `spring-data-redis` **4.1.2**. No Java/Spring Boot upgrade and no frontend package addition. Maven OSV security scanning remains NOT VERIFIED.

## Architecture decisions

- **ADR-019:** Boot-managed Spring Session Redis; namespace `cloudresilience:sessions`, default 30-minute idle timeout, 2-second connect/read timeouts, immediate flush and cleanup schedule. Login retains session ID rotation; logout invalidates the shared record. Jackson 3 uses Spring Security's modules with a `BasicPolymorphicTypeValidator` allowlist. The saved principal has no password hash or credentials; local serializer round-trip and unsafe-type rejection tests pass.
- **ADR-020:** PostgreSQL is the durable cart source of truth; Redis is not a cart/cache store.
- **ADR-021:** each cart mutation creates/finds and locks its ACTIVE cart row (`SELECT ... FOR UPDATE`). Unique database constraints protect active-cart and product-line uniqueness.
- **ADR-022:** Redis has no local fallback. Readiness includes Redis; session failures fail closed and use a generic 503 where safely handled.
- **ADR-023:** cart operations only display stock availability; no reservation, inventory update, order or payment. Checkout must revalidate price and stock.

Design detail: [`docs/03-architecture/DISTRIBUTED_STATE_CART_DESIGN.md`](../03-architecture/DISTRIBUTED_STATE_CART_DESIGN.md).

## Migration and cart integrity

Added `apps/api/src/main/resources/db/migration/V3__create_customer_cart_schema.sql`; V1/V2 are unchanged. V3 creates `carts` and `cart_items`, user/cart/product FKs, timestamp defaults, one-ACTIVE-cart-per-user partial unique index, unique `(cart_id, product_id)`, quantity check 1–9999, and cart/product lookup indexes. Cart deletion cascades lines; user/product deletion is restricted.

The migration was compiled into the build but **was not applied to PostgreSQL**. Flyway V3 and Hibernate/schema integration are BLOCKED pending Docker/Testcontainers execution.

## Sessions, security and operations

Redis host, port, password, namespace, session timeout and connect/read timeouts are externally configurable. Existing Spring Security authentication, CSRF, CUSTOMER/ADMIN roles, `/auth/me`, login and logout semantics remain. The cookie remains HttpOnly, SameSite=Lax, path `/`, `JSESSIONID`; Secure is false only for local HTTP and must be true behind production HTTPS. `ChangeSessionIdAuthenticationStrategy` is retained. Password hash/auth credentials are removed from the principal before saving the SecurityContext to Redis.

Redis has no published host port and is on the private `data` network. Optional `REDIS_PASSWORD` is used by both Redis and API. Local AOF volume is development continuity only—not HA, backup, replication or failover. API liveness is independent; readiness includes PostgreSQL and Redis. Compose profile `two-api` adds API B on loopback port 8081, sharing the `api` network alias, PostgreSQL and Redis; Nginx resolves the alias dynamically. `docker compose --profile two-api up --build` was config-validated but not run.

## Cart API and behavior

| Method/route | Access | Behavior |
|---|---|---|
| `GET /api/v1/cart` | CUSTOMER | Return/create own active cart with current product price and availability |
| `GET /api/v1/cart/summary` | CUSTOMER | Current lines, quantity and subtotal |
| `POST /api/v1/cart/items` | CUSTOMER + CSRF | Add quantity; same-product line increments |
| `PATCH /api/v1/cart/items/{itemId}` | CUSTOMER + CSRF | Set quantity 1–9999 |
| `DELETE /api/v1/cart/items/{itemId}` | CUSTOMER + CSRF | Remove owned line only |
| `DELETE /api/v1/cart/items` | CUSTOMER + CSRF | Clear lines, keep active cart |

DTO validation and response records are used; no entities are serialized. Ownership comes only from `UserPrincipal`, never a client user ID. ADMIN-only users are denied. Missing and not-owned item IDs both return 404. Product and active category are checked server-side (shared row locks prevent concurrent deactivation during add). Existing inactive lines remain visible with `productAvailable=false`. Price and stock are display-only; cart quantity may exceed stock. Full contracts: [`API_ENDPOINTS.md`](../10-deployment/API_ENDPOINTS.md); OpenAPI remains at `/v3/api-docs`.

## Frontend

Added an in-memory customer cart provider that loads after session restoration, refreshes from the API and clears on logout/401. Product cards/details can add to cart; sidebar shows quantity badge. `/cart` includes loading, error/retry, empty, quantity editing, remove/clear, subtotal, availability warnings and checkout revalidation messaging. Data comes from APIs; no fabricated items, totals or stock are used. Responsive cart styles were added. UI is not an authorization boundary; no session ID is stored in browser storage.

## Integration tests added

- `CartIntegrationTest` (8 cases): ownership/role controls, duplicate consolidation, price/availability, no inventory mutation, inactive product, concurrent first-cart/add, concurrent updates, update/remove race, transaction rollback and validation.
- `SharedSessionIntegrationTest` (1 flow): two independent HTTP API contexts sharing PostgreSQL/Redis; login on A, `/me` on B, restart B, logout on B invalidating A, TTL expiry.
- `RedisOutageIntegrationTest` (1 case): Redis outage, generic 503 and readiness failure without local fallback.
- `UserPrincipalTest`: serializable security context excludes password hash.

The Docker-backed tests compile but were not executed. Cookie transfer/rotation, Redis serialization, cross-instance read/logout, TTL, outage response, migration constraints, cart SQL/ownership and synchronized concurrency are all BLOCKED pending a Docker-capable run.

## Verification evidence

| Check | Status/result |
|---|---|
| `mvn -B -ntp -f apps/api/pom.xml clean verify` | **PASS for non-container build/tests:** 27 tests discovered, 0 failures/errors, 16 skipped; 11 non-container tests passed, including two serializer tests. |
| `mvn -B -ntp -f apps/api/pom.xml verify -DrunIntegrationTests=true` | Maven **BUILD SUCCESS**, but 16 Testcontainers tests were skipped because Docker was unavailable. Integration acceptance is **BLOCKED**, not PASS. |
| Maven compiler/package | **PASS:** Java 25 compilation and Spring Boot 4.1.2 JAR repackaging. Non-fatal existing CatalogController deprecation and JVM `sun.misc.Unsafe` warnings. |
| Web lint / typecheck | **PASS** |
| Web Vitest | **PASS:** 19 tests across 5 files, including 4 new cart UI tests. |
| Web production build | **PASS:** Vite 8.3.8 bundle produced. |
| `npm audit --audit-level=moderate` | **PASS:** zero vulnerabilities. |
| Default and `two-api` `docker compose config --quiet` | **PASS:** both Compose configurations parse. |
| `git diff --check` | **PASS** |
| Docker/Testcontainers runtime | **BLOCKED:** Docker CLI cannot connect to `//./pipe/dockerDesktopLinuxEngine` (named pipe missing). No containers, readiness, migration, Nginx/API replica, Redis outage/restart or HTTP smoke run. |
| Gitleaks | **NOT VERIFIED:** no local executable; Docker scanner unavailable. No `.env` or credentials created by this work. |
| Maven OSV scan | **NOT VERIFIED:** no local scanner and no Phase 4 hosted run; dependency resolution is not a vulnerability scan. |
| Hosted CI | **NOT RUN:** Phase 4 branch is local/unpushed. Phase 3 run `43251999066` passed on `aa9945c`; it is not Phase 4 evidence. |

## Known limitations and deferred scope

- Docker-dependent acceptance tests/runtime checks must pass before Phase 4 can be marked complete.
- Cross-instance session serialization and repository behavior remain unverified until Docker-backed integration tests run; the serializer itself has passing local SecurityContext round-trip and type-rejection tests.
- No HA Redis, cloud deployment, backup/restore or failover claim; local AOF is not a backup.
- No cache, cart expiry/merge, anonymous cart, checkout, order, payment or inventory reservation.
- Browser E2E/accessibility audit, Maven OSV, Gitleaks and hosted CI are outstanding.
- User roles in an established session remain a snapshot; a role-change revocation policy is deferred.

## Reproduction

Run frontend checks from `apps/web`: `npm ci`, `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`, `npm audit --audit-level=moderate`. Run API checks with Maven 3.9.11: `mvn -B -ntp -f apps/api/pom.xml clean verify` and, on a Docker-capable host, `mvn -B -ntp -f apps/api/pom.xml verify -DrunIntegrationTests=true`. Validate with `docker compose config --quiet`, then `docker compose --profile two-api up --build` and `docker compose ps`.

After Docker becomes available, verify APIs at `http://localhost:8080` and `http://localhost:8081`, shared login/session/logout/TTL, Redis failure/recovery, readiness, Flyway V3 and cart state. Update this report with actual integration results. Use `docker compose down` to preserve data; avoid `down -v` unless intentionally deleting PostgreSQL and Redis volumes.

## Git and completion state

Current branch is `feature/phase-4-distributed-state-cart`, based on `main` at `aa9945c`. Phase 4 changes are uncommitted. No commit, push, PR, merge, release, deployment or cloud operation occurred. Hosted CI awaits separate authorization. Because distributed-session proofs, cart/database integration, Redis outage/restart and Compose runtime remain BLOCKED, overall status remains **PARTIALLY IMPLEMENTED**.
