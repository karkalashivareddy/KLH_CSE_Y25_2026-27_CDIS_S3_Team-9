# Phase 6 event-driven processing implementation report

**Status: Implementation complete locally; Docker-backed verification pending hosted CI.** No claim is made that Flyway V5, PostgreSQL outbox leasing, RabbitMQ topology, consumer retries/dead-lettering, or Compose run until the integration tests execute on a Docker-capable runner. Skipped Docker tests are not passes.

**Date:** 2026-11-13  
**Branch:** `feature/phase-6-event-driven-processing`  
**Base commit:** `4a1b237` (`main`, Phase 5 tip)  
**Implementation commit:** `28f5dcc` (`feat: implement phase 6 event-driven order processing`)  
**Report/docs follow-up commit:** this file's commit on the same branch (see PR history).

## Scope delivered

- Flyway `V5__create_event_driven_processing_schema.sql`: `outbox_events` (statuses, attempts, leases, backoff, correlation), `processed_events` inbox, `in_app_notifications`, `order_activity`, `dead_letter_messages`, `dead_letter_replays`, with check constraints and supporting indexes.
- Transactional outbox: `OutboxWriter`, `OutboxRepository`, `OutboxPublisher`. Claim/finalize use short transactions; broker calls run outside transactions. `FOR UPDATE SKIP LOCKED` leases, expired-lease reclaim, bounded retries, exponential backoff, stable error categories, publisher confirms and mandatory-return handling.
- Versioned event contracts: `EventEnvelope`, `EventType`, `OrderEventPayloads`, `EventEnvelopeCodec`; allow-listed types enforced by an enum and a database constraint. Payloads exclude personal data and credentials.
- RabbitMQ topology: durable topic exchange, two durable consumer queues, direct retry exchange with per-consumer TTL tiers (default 5s/30s/120s), durable per-consumer DLQs, persistent messages, manual ack, bounded prefetch.
- Idempotent consumption: `EventConsumptionService` inserts the inbox row and applies the effect in one transaction; `EventMessageHandler` acknowledges only after commit; `RetryPublisher` retries, quarantines, and dead-letters.
- Two meaningful consumers: `order-notifications` persists customer in-app notifications; `order-activity` maintains an append-only reporting projection.
- Order integration: `OrderPlaced` and `OrderPaymentFailed` are written with checkout/payment state; `OrderCancelled`, `OrderProcessingStarted`, `OrderShipped`, `OrderDelivered` are written with workflow transitions.
- Operational APIs: customer notification list/read; admin outbox summary, dead-letter list, reasoned replay, order activity. Health groups for readiness and messaging (`outbox`, `eventQueues`, `rabbit`).
- Configuration, Compose RabbitMQ service, `.env.example`, README, local-development and API docs, event architecture/catalog, ADR-029–032, and this report.

## Verification results

| Check | Result | Evidence |
|---|---|---|
| `mvn -B -ntp -f apps/api/pom.xml -DskipTests compile` | **PASS** | 69 main sources compiled, Java 25 |
| `mvn -B -ntp -f apps/api/pom.xml test-compile` | **PASS** | 18 test sources compiled |
| `mvn -B -ntp -f apps/api/pom.xml test` | **PASS (unit only)** | 64 tests, 29 passed, 35 skipped, 0 failures, 0 errors |
| New unit tests | **PASS** | `EventEnvelopeCodecTest` 3, `EventTopologyTest` 3, `OutboxPublisherTest` 4 |
| Docker-backed integration tests | **SKIPPED / NOT RUN** | 35 skipped: `EventDrivenIntegrationTest` 6, `OutboxLeaseIntegrationTest` 4, `OrderIntegrationTest` 9, `CartIntegrationTest` 8, `IdentityIntegrationTest` 2, `RedisOutageIntegrationTest` 1, `SharedSessionIntegrationTest` 1, `InventoryConcurrencyIntegrationTest` 4 |
| Flyway V1–V5 on PostgreSQL | **NOT VERIFIED LOCALLY** | Docker engine unavailable (`docker info` found no engine) |
| RabbitMQ topology/retry/DLQ/replay | **NOT VERIFIED LOCALLY** | Requires the skipped `EventDrivenIntegrationTest` |
| Compose `config`/`up` | **NOT RUN** | Docker engine unavailable; Compose file updated but not executed |
| Frontend `npm run lint` | **PASS** | eslint clean |
| Frontend `npm run typecheck` | **PASS** | `tsc -b` clean |
| Frontend `npm test -- --reporter=dot` | **PASS** | 21 tests in 6 files |
| Frontend `npm run build` | **PASS** | 29 modules transformed; production build written |
| `git diff --check` | **PASS** | no whitespace errors |
| Gitleaks / dependency vulnerability scan | **NOT RUN** | not executed in this phase |
| Hosted CI (`runIntegrationTests=true`) | **PENDING** | to be recorded from the PR checks |

## Test intent added

- `EventEnvelopeCodecTest`: envelope round-trip, required-field/version validation, malformed body rejection.
- `EventTopologyTest`: stable routing keys, retry tier selection/clamping, bounded retry constant, exchange names.
- `OutboxPublisherTest`: mark-published only after confirm, failure category/backoff, disabled publisher claims nothing, unroutable messages are not published.
- `OutboxLeaseIntegrationTest` (gated): successful confirm finalizes; broker failure leaves a committed event `PENDING` with backoff; concurrent publishers cannot claim the same active lease; expired leases are reclaimed.
- `EventDrivenIntegrationTest` (gated): end-to-end publish to both projections; duplicate delivery yields one effect per consumer; a poison payload retries then quarantines without committing the inbox row; unsupported version quarantines immediately; publisher crash after confirm is recovered without duplicate effects; controlled dead-letter replay is audited and idempotent.
- `OrderIntegrationTest` additions: outbox rows are written in the checkout/cancellation/fulfilment transaction and payloads exclude email/password/session/CSRF data.

## Blockers and limitations

- The local machine has no usable Docker engine, so all Testcontainers and Compose runtime claims are blocked. The hosted CI job is the intended Docker-capable verifier.
- Delivery is at-least-once; there is no exactly-once or ordered delivery claim. Events for one aggregate may be published out of order under retries.
- The publisher runs in the API process. There is no dedicated worker, leader election, inbox retention/archival, automatic DLQ drain, or bulk replay.
- Notifications are persisted in-app only. No email, SMS, push, or external provider is integrated.
- `OrderPaymentSucceeded` is a reserved contract but is not emitted in Phase 6; `OrderPlaced` represents a confirmed simulated payment.
- No observability backend (metrics/traces/log aggregation), load test, or alerting exists yet; these are Phase 7 scope. Health endpoints report outbox/queue state only.
- No cloud resources, managed broker, Terraform, or deployment were created or applied.

## Required follow-up (hosted CI)

1. Run the API job with `runIntegrationTests=true` and record the exact outcome, including Flyway V1–V5 startup and all event integration cases.
2. Fix any runtime failures before claiming Phase 6 verified; re-run until green.
3. Run `docker compose config` and, where feasible, `docker compose up --build` to verify RabbitMQ wiring and the messaging health group.
4. Run secret/dependency scans and record results; add Phase 7 observability rather than retrofitting it here.

## Git state

- Branch created from `main` at `4a1b237`; implementation commit `28f5dcc`.
- No force-push, history rewrite, cloud provisioning, or deployment performed.
- PR URL and hosted CI URL are recorded in the PR and final handoff.
