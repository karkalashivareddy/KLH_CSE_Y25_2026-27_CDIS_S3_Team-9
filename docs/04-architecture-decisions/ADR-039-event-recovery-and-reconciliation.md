# ADR-039: Event recovery follows PostgreSQL authority

- **Status:** Accepted as a design direction; recovery reconciliation drill pending
- **Date:** 2026-11-18

## Context

PostgreSQL commits each business change with its outbox event. RabbitMQ publication is at-least-once and can duplicate when a publisher crashes after broker confirmation. Consumers use a durable inbox for idempotency. Broker state and database state do not share a transaction.

## Decision

After a database restore, preserve outbox and inbox records. Restore broker topology/state where supported, inspect pending events and broker/DLQ queues, and reconcile with consumers stopped before resuming. Resume the publisher and consumers only after event versions and consumer idempotency are confirmed. DLQ replay requires an audited operator decision; never blindly republish or clear deduplication records.

## Consequences

Recovery may temporarily delay asynchronous effects. Duplicate delivery is expected and must remain safe. PostgreSQL remains authoritative for order, inventory, reservation and payment state; messaging cannot independently recreate missing business transactions.

## Validation

Crash/replay recovery, backup restore with pending events, and DLQ replay audit: NOT RUN in a Phase 9 drill. No cloud broker is provisioned.
