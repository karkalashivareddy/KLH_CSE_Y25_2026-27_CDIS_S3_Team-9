# Phase 6 event-driven processing implementation report

**Status: Phase 6 implementation and local Docker-backed verification pass; hosted CI and GitHub integration remain pending.** The verification below ran in the working tree after the listed regression fixes. No cloud resources were provisioned.

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
| `mvn -B -ntp -Dmaven.repo.local=<user-cache> -f apps/api/pom.xml verify -DrunIntegrationTests=true` | **PASS** | 64 tests, 64 passed, 0 skipped, 0 failures, 0 errors; packaged successfully on Java 25 with Docker 29.7.2 |
| New unit tests | **PASS** | `EventEnvelopeCodecTest` 3, `EventTopologyTest` 3, `OutboxPublisherTest` 4 |
| Docker-backed integration tests | **PASS** | All event, checkout, cart, inventory, identity, Redis outage, and shared-session cases executed; 0 skipped |
| Flyway V1–V5 on PostgreSQL 17.7 | **PASS** | Applied and validated in Testcontainers |
| RabbitMQ topology/retry/DLQ/replay | **PASS** | `EventDrivenIntegrationTest` and `OutboxLeaseIntegrationTest` executed |
| `docker compose config --quiet` | **PASS** | Compose configuration parsed; services were not started as a full application stack |
| Frontend `npm run lint` | **PASS** | eslint clean |
| Frontend `npm run typecheck` | **PASS** | `tsc -b` clean |
| Frontend `npm test -- --reporter=dot` | **PASS** | 21 tests in 6 files |
| Frontend `npm run build` | **PASS** | 29 modules transformed; production build written |
| `npm.cmd audit --audit-level=moderate` | **PASS** | 0 vulnerabilities |
| `git diff --check` | **PASS** | no whitespace errors |
| Gitleaks / OSV Maven scan | **NOT RUN LOCALLY** | scanner CLIs are unavailable; hosted workflow has not been inspected |
| Hosted CI (`runIntegrationTests=true`) | **NOT VERIFIED** | `gh` authentication token is invalid, so hosted run status could not be queried |

## Test intent added

- `EventEnvelopeCodecTest`: envelope round-trip, required-field/version validation, malformed body rejection.
- `EventTopologyTest`: stable routing keys, retry tier selection/clamping, bounded retry constant, exchange names.
- `OutboxPublisherTest`: mark-published only after confirm, failure category/backoff, disabled publisher claims nothing, unroutable messages are not published.
- `OutboxLeaseIntegrationTest` (gated): successful confirm finalizes; broker failure leaves a committed event `PENDING` with backoff; concurrent publishers cannot claim the same active lease; expired leases are reclaimed.
- `EventDrivenIntegrationTest` (gated): end-to-end publish to both projections; duplicate delivery yields one effect per consumer; a poison payload retries then quarantines without committing the inbox row; unsupported version quarantines immediately; publisher crash after confirm is recovered without duplicate effects; controlled dead-letter replay is audited and idempotent.
- `OrderIntegrationTest` additions: outbox rows are written in the checkout/cancellation/fulfilment transaction and payloads exclude email/password/session/CSRF data.

## Blockers and limitations

- The hosted CI result and required hosted security jobs remain unverified because the configured GitHub CLI credential is invalid.
- The full Compose application stack was not started; only Compose syntax/configuration validation ran.
- Delivery is at-least-once; there is no exactly-once or ordered delivery claim. Events for one aggregate may be published out of order under retries.
- The publisher runs in the API process. There is no dedicated worker, leader election, inbox retention/archival, automatic DLQ drain, or bulk replay.
- Notifications are persisted in-app only. No email, SMS, push, or external provider is integrated.
- `OrderPaymentSucceeded` is a reserved contract but is not emitted in Phase 6; `OrderPlaced` represents a confirmed simulated payment.
- No observability backend (metrics/traces/log aggregation), load test, or alerting exists yet; these are Phase 7 scope. Health endpoints report outbox/queue state only.
- No cloud resources, managed broker, Terraform, or deployment were created or applied.

## Required follow-up (main-only GitHub integration)

1. Restore GitHub CLI authentication and inspect existing pull requests/checks.
2. Close any open pull requests and remove obsolete feature branches only after their commits are verified on `main`.
3. Push Phase 6 directly to `main` after required local checks pass; allow the push-triggered workflow to run and do not bypass branch protection or failed checks.
4. Confirm Gitleaks and OSV jobs complete successfully against the pushed `main` commit, then address any failed check with a corrective commit.

## Git state

- Branch created from `main` at `4a1b237`; implementation commit `28f5dcc`; local regression corrections are currently uncommitted.
- No force-push, history rewrite, cloud provisioning, or deployment performed.
- Hosted checks and pull request state are not verified in this report.
