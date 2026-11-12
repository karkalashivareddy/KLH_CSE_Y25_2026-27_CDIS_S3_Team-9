# Database design

**Purpose/scope:** conceptual PostgreSQL model, not schema migration scope for Phase 1. See [ER diagram](ENTITY_RELATIONSHIPS.md), data architecture, SEC-003/006 and ADR-003.

| Entity | Responsibility / key integrity |
|---|---|
| User | UUID PK; unique normalized email; password hash only; personal data classified. |
| Role, Permission | Stable keys; many-to-many role-permission and user-role joins with unique pairs. |
| Product, Category | Product SKU unique; category parent optional; product/category join if multi-category. |
| Inventory | One row per product/location (unique pair); nonnegative available/reserved quantities; version for optimistic updates. |
| Cart, CartItem | Cart owner FK; unique product per cart; quantity > 0; transient lifecycle/expiry. |
| Order, OrderItem | Owner FK; unique idempotency key scoped to customer; immutable item/price snapshots; state transitions audited. |
| PaymentSimulation | Order FK unique per attempt/reference; synthetic result/token only, no PAN/CVV. |
| Address | User-owned encrypted or minimized personal fields; references snapshotted where order retention requires. |
| AuditEvent | Append-only actor/action/target/time/correlation; restrict payload to non-sensitive metadata. |
| Incident, RecoveryExecution | Incident ownership and timeline; execution links incident, approver, environment, result and evidence. |
| CloudResource, CostRecord | Provider/account/resource identifiers classified sensitive; cost observation has currency, unit, time range, source and estimate/actual flag. |

Indexes: unique normalized user email, SKU, scoped idempotency key; order `(user_id, created_at DESC)`; inventory product/location; incident/recovery status/time; cost provider/resource/time. Add based on measured queries. Transactions: order+reservation+outbox atomic; payment simulation is separately idempotent; external events use outbox/inbox. Retention/deletion/export obligations require policy and jurisdiction decisions. Validation: migration tests, constraints, concurrency tests, restore and access-control review. No database tables are created in this phase.
