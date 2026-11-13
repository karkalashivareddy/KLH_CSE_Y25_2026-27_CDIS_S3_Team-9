# ADR-029: Transactional outbox for order events

- **Status:** Accepted for Phase 6
- **Context:** Publishing to a broker inside the order transaction can lose events when the broker is unavailable after the database commits, and committing after a broker publish can emit events for rolled-back orders. A distributed transaction between PostgreSQL and RabbitMQ is not justified for this project.
- **Decision:** Write each order event to `outbox_events` in the same PostgreSQL transaction as the business change. A scheduled publisher claims due rows with `FOR UPDATE SKIP LOCKED`, publishes outside any database transaction with publisher confirms and mandatory routing, then marks the row `PUBLISHED`. Failures return the row to `PENDING` with exponential backoff, a bounded retry count, a stable error category, and eventually `FAILED`. Expired `PROCESSING` leases are reclaimable.
- **Consequences:** PostgreSQL remains the source of truth and a broker outage cannot roll back or lose committed events. Delivery is at-least-once, so duplicate publication after a crash is expected. The publisher runs inside the API process for now; a dedicated worker and ordering guarantees are deferred.
- **Rejected alternatives:** Dual write without an outbox (loss window); XA/2PC (operational complexity and poor RabbitMQ support); Kafka (heavier than required for the initial single-region design).
