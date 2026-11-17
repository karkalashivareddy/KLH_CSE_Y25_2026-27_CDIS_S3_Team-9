# Phase 6 event-driven processing implementation report

**Status: Phase 6 implementation and local Docker-backed verification pass; hosted CI is green on `main`.** No cloud resources were provisioned.

**Date:** 2026-11-17
**Branch:** `main`
**Base commit:** `4a1b237` (`main`, Phase 5 tip)  
**Implementation commit:** `28f5dcc` (`feat: implement phase 6 event-driven order processing`)  
**Main integration commits:** `0df399f` (verified regression fixes), `88d1a9c` (RabbitMQ dependency security fix)

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
| `mvn -B -ntp -Dmaven.repo.local=<user-cache> -f apps/api/pom.xml verify -DrunIntegrationTests=true` | **PASS** | 64 tests, 64 passed, 0 skipped, 0 failures, 0 errors against RabbitMQ client 5.37.1; Java 25, Docker 29.7.2 |
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
| Gitleaks | **PASS (hosted)** | CI run 44022105540 secret-scan job passed |
| OSV Maven scan | **PASS (hosted)** | CI run 44023777571 passed; RabbitMQ client is pinned to 5.37.1 and the open code-scanning alert list is empty |
| Hosted API/Web checks | **PASS (hosted)** | CI run 44023777571 passed API integration and Web checks on `88d1a9c` |
| Hosted Gitleaks | **PASS** | CI run 44023777571 secret-scan job passed |
| Hosted CI | **PASS** | [Run 44023777571](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44023777571) tested exact `main` head `88d1a9c9cd376053a639a92b6472eacb09803eca`; all jobs succeeded |

## Test intent added

- `EventEnvelopeCodecTest`: envelope round-trip, required-field/version validation, malformed body rejection.
- `EventTopologyTest`: stable routing keys, retry tier selection/clamping, bounded retry constant, exchange names.
- `OutboxPublisherTest`: mark-published only after confirm, failure category/backoff, disabled publisher claims nothing, unroutable messages are not published.
- `OutboxLeaseIntegrationTest` (gated): successful confirm finalizes; broker failure leaves a committed event `PENDING` with backoff; concurrent publishers cannot claim the same active lease; expired leases are reclaimed.
- `EventDrivenIntegrationTest` (gated): end-to-end publish to both projections; duplicate delivery yields one effect per consumer; a poison payload retries then quarantines without committing the inbox row; unsupported version quarantines immediately; publisher crash after confirm is recovered without duplicate effects; controlled dead-letter replay is audited and idempotent.
- `OrderIntegrationTest` additions: outbox rows are written in the checkout/cancellation/fulfilment transaction and payloads exclude email/password/session/CSRF data.

## Blockers and limitations

- The full Compose application stack was not started; only Compose syntax/configuration validation ran.
- Delivery is at-least-once; there is no exactly-once or ordered delivery claim. Events for one aggregate may be published out of order under retries.
- The publisher runs in the API process. There is no dedicated worker, leader election, inbox retention/archival, automatic DLQ drain, or bulk replay.
- Notifications are persisted in-app only. No email, SMS, push, or external provider is integrated.
- `OrderPaymentSucceeded` is a reserved contract but is not emitted in Phase 6; `OrderPlaced` represents a confirmed simulated payment.
- No observability backend (metrics/traces/log aggregation), load test, or alerting exists yet; these are Phase 7 scope. Health endpoints report outbox/queue state only.
- No cloud resources, managed broker, Terraform, or deployment were created or applied.

## GitHub integration and cleanup

1. All currently open PRs: **none**. PR #2 is merged and PR #1 is closed. GitHub preserves closed/merged PR records; it does not support deleting those records.
2. The Phase 4, Phase 5 and Phase 6 feature branches were removed after verifying their tips were ancestors of `origin/main`.

## Git state

- At verification time, local and remote branches contained only `main`, at `88d1a9c9cd376053a639a92b6472eacb09803eca`. The report/README update is documentation-only.
- Working tree is clean at report time. CI run 44023777571 is green; no open PRs or OSV code-scanning alerts remain.
- No force-push, history rewrite, cloud provisioning, or deployment performed.
- No open PRs were found; GitHub reports PR #2 merged and PR #1 closed.
