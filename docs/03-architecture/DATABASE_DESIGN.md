# Database design

**Purpose/scope:** conceptual target model; Phase 2 implements only identity tables through Flyway. See [identity schema](IDENTITY_SCHEMA.md), [ER diagram](ENTITY_RELATIONSHIPS.md), data architecture, SEC-003/006 and ADR-003/011/012.

| Entity | Responsibility / key integrity |
|---|---|
| User | **Implemented:** UUID PK; unique normalized email; BCrypt password hash; names; enabled/locked flags; UTC timestamps. |
| Role | **Implemented:** UUID PK, unique CUSTOMER/ADMIN name; `user_roles` join with a unique composite PK. Permissions remain planned. |
| Product, Category | Product SKU unique; category parent optional; product/category join if multi-category. |
| Inventory | One row per product/location (unique pair); nonnegative available/reserved quantities; version for optimistic updates. |
| Cart, CartItem | Cart owner FK; unique product per cart; quantity > 0; transient lifecycle/expiry. |
| Order, OrderItem | Owner FK; unique idempotency key scoped to customer; immutable item/price snapshots; state transitions audited. |
| PaymentSimulation | Order FK unique per attempt/reference; synthetic result/token only, no PAN/CVV. |
| Address | User-owned encrypted or minimized personal fields; references snapshotted where order retention requires. |
| AuditEvent | Append-only actor/action/target/time/correlation; restrict payload to non-sensitive metadata. |
| Incident, RecoveryExecution | Incident ownership and timeline; execution links incident, approver, environment, result and evidence. |
| CloudResource, CostRecord | Provider/account/resource identifiers classified sensitive; cost observation has currency, unit, time range, source and estimate/actual flag. |

Indexes: implemented normalized user email and role join reverse lookup; SKU, scoped idempotency key, order `(user_id, created_at DESC)`, inventory product/location, incident/recovery status/time and cost provider/resource/time remain future. Transactions: order+reservation+outbox atomic; payment simulation is separately idempotent; external events use outbox/inbox. Retention/deletion/export obligations require policy and jurisdiction decisions. PostgreSQL integration coverage uses Testcontainers and is Docker-dependent; application startup validates the migrated schema rather than generating it.
