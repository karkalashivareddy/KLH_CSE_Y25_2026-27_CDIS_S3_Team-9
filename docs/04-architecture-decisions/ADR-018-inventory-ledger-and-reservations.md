# ADR-018: Inventory reservations and append-only movement ledger

- **Status:** Accepted for Phase 3
- **Date:** 2026-11-12
- **Context:** on-hand stock, reserved stock, and later order fulfillment need traceable changes and a safe release lifecycle.
- **Decision:** store `quantity_on_hand` and `quantity_reserved`; derive availability as their difference. Store reservation lifecycle rows with immutable quantity/product/actor and RESERVED/RELEASED status. Every initial stock, adjustment, reservation and release transaction appends an `inventory_movements` event containing signed on-hand delta, reserved delta, before/after snapshots, reason, optional reference, actor and timestamp. Reservations do not decrement on-hand; releases do not increment on-hand.
- **Integrity:** constraints enforce non-negative on-hand/reserved and reserved <= on-hand. Movement records are Hibernate immutable, have no update/delete repository/API, and a PostgreSQL trigger rejects row updates/deletes. Corrections are new signed movements, never edits to history.
- **Access:** manual stock, reservation, and release HTTP endpoints are ADMIN-only in Phase 3 because cart/order ownership/lifecycle is out of scope. Customers cannot modify reservation quantities. A transactional application service is the future trusted order boundary; public clients cannot invoke it directly.
- **Trade-offs:** the event ledger adds writes/storage but provides an audit trail and deterministic before/after evidence. It is not a general accounting ledger and does not yet model carts/orders.
- **Validation:** Testcontainers verifies table constraints, transaction rollback, append-only enforcement, actor attribution, and concurrent final-unit reservation behavior.
