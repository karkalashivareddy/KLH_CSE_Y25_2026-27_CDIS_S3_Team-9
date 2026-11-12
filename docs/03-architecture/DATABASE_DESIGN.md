# Database design

**Purpose/scope:** Phase 2 identity and Phase 3 catalog/inventory are implemented in PostgreSQL through Flyway V1/V2. Cart, order, payment, audit and recovery entities remain conceptual. See [identity schema](IDENTITY_SCHEMA.md), [catalog/inventory design](CATALOG_INVENTORY_DESIGN.md), [ER diagram](ENTITY_RELATIONSHIPS.md), and ADR-003/011/012/016/017/018.

| Entity | Responsibility / key integrity |
|---|---|
| User | **Implemented:** UUID PK; unique normalized email; BCrypt password hash; names; enabled/locked flags; UTC timestamps. |
| Role | **Implemented:** UUID PK, unique CUSTOMER/ADMIN name; `user_roles` join with a unique composite PK. Permissions remain planned. |
| Category | **Implemented V2:** flat categories, unique slug, active flag and timestamps; product FK restricts deletion. |
| Product | **Implemented V2:** unique uppercase SKU and slug; positive `NUMERIC(12,2)` USD price; DRAFT/ACTIVE/ARCHIVED status; optional HTTPS image URL; category FK; archived rather than deleted. |
| Inventory | **Implemented V2:** one row per product; nonnegative on-hand and reserved values with reserved <= on-hand; availability derived; row-level locks serialize transactions. |
| InventoryReservation | **Implemented V2:** positive quantity, RESERVED/RELEASED lifecycle, product and actor FKs. |
| InventoryMovement | **Implemented V2:** append-only signed on-hand/reserved deltas and before/after snapshots, reason, reference and actor; trigger rejects update/delete. |
| Cart, CartItem | Cart owner FK; unique product per cart; quantity > 0; transient lifecycle/expiry. |
| Order, OrderItem | Owner FK; unique idempotency key scoped to customer; immutable item/price snapshots; state transitions audited. |
| PaymentSimulation | Order FK unique per attempt/reference; synthetic result/token only, no PAN/CVV. |
| Address | User-owned encrypted or minimized personal fields; references snapshotted where order retention requires. |
| AuditEvent | Append-only actor/action/target/time/correlation; restrict payload to non-sensitive metadata. |
| Incident, RecoveryExecution | Incident ownership and timeline; execution links incident, approver, environment, result and evidence. |
| CloudResource, CostRecord | Provider/account/resource identifiers classified sensitive; cost observation has currency, unit, time range, source and estimate/actual flag. |

Indexes: normalized user email; product public category/status/name and status/created time; lowercased product name; reservation product/status; movement product/time. Transactions: inventory row lock plus reservation/inventory/movement writes are atomic; order/outbox and other future domain concerns remain deferred. PostgreSQL schema is migration-managed and startup validates it; PostgreSQL Testcontainers execution requires Docker availability to the test process.
