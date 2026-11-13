# Phase 5 order management, checkout, and simulated payment report

**Status: Implementation complete; Docker-backed verification blocked.** No claim is made that V3/V4 migrations, database transactions, Redis sessions, Compose, or checkout concurrency work at runtime until their integration tests execute on a Docker-capable host.

**Date:** 2026-11-13  
**Branch:** `feature/phase-5-orders-checkout-payments` (local; based on `aa9945c`)  
**Commit/push/merge/deploy:** none

## Scope delivered

- Flyway V4 adds order headers and immutable item snapshots, payment intents/results, customer-scoped database idempotency, status history, order-linked reservations and inventory movement types. V4 extends V3 reservation lifecycle with COMMITTED state and timestamps.
- Checkout requires `Idempotency-Key`, accepts only product IDs/quantities, checks exact match with the server's active PostgreSQL cart, locks cart/catalog/inventory rows, validates all stock, and persists order/snapshots/reservations/payment intent atomically. The simulator is called between two DB transactions; deterministic simulator outcomes never contact an external provider or transfer money.
- Same-key retries return/resume the same persisted order/payment; changed payload conflicts. Payment failure and eligible cancellation release reservations once; moving a confirmed paid order to PROCESSING consumes them once and records inventory movement/history.
- Customer APIs/UI provide checkout, order history/details, simulated payment state, and cancellation of eligible owned orders. ADMIN APIs/UI provide order search/filter/summary/detail and controlled fulfillment transitions.
- ADR-024–028, `ORDER_CHECKOUT_DESIGN.md`, API endpoint documentation and README scope/status were updated. Automatic reservation expiry, real payments/refunds, taxes, shipping, and remote-provider recovery remain deferred.

## Verification results

| Check | Result |
|---|---|
| `mvn -B -ntp -f apps/api/pom.xml test` | **PASS** — 43 tests, 0 failures, 0 errors, 24 skipped. All 19 non-container tests passed, including state-machine, pricing and deterministic simulator tests. |
| Docker-backed Testcontainers tests | **NOT RUN / SKIPPED** — Docker environment unavailable (`NpipeSocketClientProviderStrategy` found no valid engine). The 24 skipped tests are not passes. Includes 8 new order integration tests plus Phase 4 cart/session and inventory integration gates. |
| Frontend `npm run typecheck` | **PASS** |
| Frontend `npm run lint` | **PASS** |
| Frontend `npm run build` | **PASS** |
| Frontend `npm test -- --reporter=dot` | **PASS** — 21 tests across 6 files. |
| Gitleaks, dependency vulnerability scan, hosted CI | **NOT VERIFIED / NOT RUN** |

## Blockers and limitations

The Phase 4 prerequisite report still marks V3 migration, PostgreSQL cart/ownership behavior, Redis shared-session behavior and Compose runtime as unverified. V4 depends on V3, so the current schema and migration ordering have not been exercised. Atomic reservation rollback, same-key concurrency, final-unit races, customer ownership, status checks and cancellation/commit lifecycle have integration-test coverage in source but no runtime evidence until Docker is available. No database client/server was used to independently execute V4 SQL.

Checkout is synchronous with a local simulator. The persisted PROCESSING intent can be resumed by retrying the same key, but automatic expiry/reconciliation is not implemented. This is not a suitable remote payment-provider integration pattern; a future real gateway needs a durable outbox and unknown-outcome reconciliation. Frontend checkout reuses one idempotency key for retries within the current page component; navigating away and beginning another checkout creates a new intent.

## Required follow-up

1. On a Docker-capable host run `mvn -B -ntp -f apps/api/pom.xml verify -DrunIntegrationTests=true`; inspect Flyway V1–V4 application, Hibernate validation, and all database/Redis integration cases. Fix any runtime failures before treating Phase 5 as verified.
2. Run `docker compose --profile two-api up --build` and verify Phase 4 shared-session/fail-closed gates and frontend API behavior if the profile is supported.
3. Keep the frontend checks green as the feature evolves; the final local run passed typecheck, lint, 21 tests, and production build.
4. Run required secret/dependency scans and hosted CI; record their exact outcomes.
5. Preserve the shared Phase 4 uncommitted work. No commit, push, merge, deployment, or cloud operation was performed.
