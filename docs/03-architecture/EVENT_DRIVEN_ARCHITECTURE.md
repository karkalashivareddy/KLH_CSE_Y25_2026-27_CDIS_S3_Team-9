# Event-driven order processing

**Status:** Implemented for Phase 6; local unit tests pass and Docker-backed PostgreSQL/RabbitMQ verification is delegated to hosted CI. No production broker, managed queue, or cross-region replication is claimed.

## Goals and non-goals

The goal is to decouple customer-facing order writes from downstream notification and reporting work without losing events when the broker or a consumer is unavailable. PostgreSQL remains the source of truth. RabbitMQ is the initial local/hosted-CI transport, hidden behind a small publisher and consumer boundary so a managed broker can replace it later.

Non-goals for Phase 6: exactly-once end-to-end delivery, a real email/push provider, event sourcing, Kafka, cross-region replication, and autonomous replay. Delivery is at-least-once with idempotent consumers.

## Write path: transactional outbox

1. The order/checkout transaction performs its normal PostgreSQL writes.
2. In the same transaction, `OutboxWriter` inserts one `outbox_events` row containing the full versioned envelope. A rollback therefore removes the business change and its event together.
3. A scheduled publisher (`OutboxPublisher`) claims due rows with `FOR UPDATE SKIP LOCKED`. Claiming sets `status=PROCESSING`, a unique `lease_owner`, and `lease_expires_at`.
4. The broker call happens outside any database transaction. The message is persistent and published to `cloudresilience.events` with publisher confirms and mandatory routing.
5. On an ack and a routable message, `markPublished` sets `PUBLISHED` and `published_at`. On failure, the row returns to `PENDING` with incremented `attempt_count`, an error category, and exponential backoff; after the configured maximum attempts it becomes `FAILED`.
6. An expired `PROCESSING` lease (crashed publisher) is reclaimable by another instance.

Because steps 3 and 5 are separate short transactions, a crash between broker confirmation and finalization republishes the same event. That duplicate is expected and safe.

### Outbox states

| State | Meaning |
|---|---|
| `PENDING` | Committed, due at `next_attempt_at`, not currently leased |
| `PROCESSING` | Leased by one publisher until `lease_expires_at` |
| `PUBLISHED` | Broker acknowledged and the message was routable |
| `FAILED` | Maximum attempts exhausted; requires operator inspection |

## Broker topology

Declared in code (`EventTopology`) so it is reviewable and versioned:

- Topic exchange `cloudresilience.events`; order events use routing keys such as `order.placed` and `order.shipped`, and both consumer queues bind `order.#`.
- Durable consumer queues: `cloudresilience.order.notifications` and `cloudresilience.order.activity`.
- Direct retry exchange `cloudresilience.events.retry` with one TTL queue per consumer and tier. TTL expiry dead-letters through the default exchange back to the original consumer queue, retaining the message identity and retry header.
- Dead-letter exchange `cloudresilience.events.dlx` with durable queues `...notifications.dlq` and `...activity.dlq`.
- Persistent messages, manual acknowledgement, bounded prefetch, and no plugin dependency.

Default retry tiers are 5s, 30s and 120s; the maximum is 3 retries before quarantine. Tier values and maximum attempts are configuration, not hard-coded business rules.

## Read path: idempotent consumers

Both consumers share `EventMessageHandler`:

1. Decode the envelope. Malformed JSON is non-retryable.
2. Reject unknown event types or versions other than the supported version. Unsupported versions are quarantined immediately rather than retried.
3. In one database transaction, insert `processed_events(event_id, consumer_name)` with `ON CONFLICT DO NOTHING` and run the consumer's durable effect. If the inbox row already exists, the effect is skipped.
4. Acknowledge only after the transaction commits.
5. On failure, hand the message to `RetryPublisher`. It republishes to the tiered retry exchange and acknowledges the original. If retry publication itself fails, it nacks the original for redelivery. After the last attempt it writes a `dead_letter_messages` row, publishes to the durable DLQ, and only then acknowledges.

A crash before commit leaves no inbox row, so redelivery applies the effect. A crash after commit but before acknowledgement causes redelivery, but the inbox primary key makes it a no-op.

## Consumers

| Consumer | Queue | Durable effect |
|---|---|---|
| `order-notifications` | `cloudresilience.order.notifications` | Inserts customer-visible `in_app_notifications` rows. Does not send email/SMS/push. |
| `order-activity` | `cloudresilience.order.activity` | Inserts an append-only `order_activity` reporting projection with the event's order status. |

The projections are intentionally independent of authoritative order tables so a projection failure cannot roll back an order, and rebuilding them from the outbox is possible.

## Operator controls

- `GET /api/v1/admin/events/outbox` returns per-status counts and oldest pending age.
- `GET /api/v1/admin/events/dead-letters` lists quarantined/replayed messages.
- `POST /api/v1/admin/events/dead-letters/{id}/replay` requires a reason, publishes first, then records `dead_letter_replays` and marks `REPLAYED`. A second replay of the same row is rejected with 409. Replay is manually triggered; no automatic drain exists.
- `GET /api/v1/admin/events/orders/{orderId}/activity` reads the activity projection.
- `GET /api/v1/notifications` and `POST /api/v1/notifications/{id}/read` serve customer notifications.

Health:

| Group | Includes | Meaning |
|---|---|---|
| `/actuator/health/readiness` | `readinessState,db,redis` | Can serve HTTP traffic |
| `/actuator/health/messaging` | `outbox,eventQueues,rabbit` | Outbox backlog, consumer/DLQ depth, broker connectivity |

A broker outage does not fail readiness; committed events stay `PENDING` and drain after recovery. A `FAILED` outbox row makes the outbox component `DEGRADED`, which is visible but still not a readiness failure.

## Failure semantics

| Failure | Behavior |
|---|---|
| Broker unavailable at publish time | Row stays `PENDING`, backoff grows, order remains committed |
| Publisher crashes after confirm, before finalize | Expired lease is reclaimed and the event is republished; consumers deduplicate |
| Consumer crashes before commit | Redelivery applies the effect once |
| Consumer crashes after commit, before ack | Redelivery is skipped by the inbox |
| Poison payload / unknown type / unsupported version | Bounded retries for poison payloads; immediate quarantine for malformed/unsupported messages |
| Retry publication fails | Original is nacked for redelivery rather than acked |
| DLQ write fails | Message is not acknowledged, so the broker retains it |
| Database unavailable | Consumer transaction fails; message is retried/quarantined by policy; outbox health fails |

## Security and privacy

Events contain identifiers, order number, status, payment status, amounts, currency, item count, and (for cancellations/transitions) a bounded reason. They exclude email addresses, names, addresses, credentials, session identifiers, CSRF tokens and payment instrument data. Admin replay and dead-letter inspection require the `ADMIN` role; notification reads are customer-owned. Queue access is restricted to the application network in Compose.

## Known limitations

- At-least-once only; there is no distributed transaction or broker/database two-phase commit.
- The inbox grows without a retention job; archival is deferred.
- Replay is single-message and operator-driven. There is no bulk drain, scheduled reconciliation, or automatic poison-message classification beyond error categories.
- The outbox publisher runs in the API process. A dedicated worker, leader election, and per-event ordering guarantees are deferred. Multiple API replicas are safe because of row leases, but ordering between events for one aggregate is not guaranteed.
- RabbitMQ is a single local/CI container. Quorum queues, managed HA, cross-region replication, and broker-level flow control are not exercised.
- No real notification delivery, no email/push provider, and no consumer-side observability backend yet (Phase 7).
