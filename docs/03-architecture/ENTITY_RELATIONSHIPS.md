# Entity relationships

**Purpose/scope:** identity, catalog, inventory, and customer cart relationships are implemented through Flyway V1–V3; order and resilience relationships remain conceptual. See [database design](DATABASE_DESIGN.md), [identity schema](IDENTITY_SCHEMA.md) and [catalog/inventory design](CATALOG_INVENTORY_DESIGN.md).

```mermaid
erDiagram
  USER ||--o{ CART : owns
  USER ||--o{ INVENTORY_RESERVATION : creates
  USER ||--o{ INVENTORY_MOVEMENT : performs
  USER ||--o{ ORDER : places
  USER ||--o{ ADDRESS : saves
  USER }o--o{ ROLE : assigned
  ROLE }o--o{ PERMISSION : grants
  CATEGORY ||--o{ PRODUCT : classifies
  PRODUCT ||--o| INVENTORY : stock
  PRODUCT ||--o{ INVENTORY_RESERVATION : reserves
  PRODUCT ||--o{ INVENTORY_MOVEMENT : records
  CART ||--|{ CART_ITEM : contains
  PRODUCT ||--o{ CART_ITEM : selected
  ORDER ||--|{ ORDER_ITEM : snapshots
  PRODUCT ||--o{ ORDER_ITEM : referenced
  ORDER ||--o{ PAYMENT_SIMULATION : simulates
  USER ||--o{ AUDIT_EVENT : acts
  INCIDENT ||--o{ RECOVERY_EXECUTION : tracks
  CLOUD_RESOURCE ||--o{ COST_RECORD : incurs
```

Addresses may be copied into a future order snapshot to preserve shipping history. The implemented category/product relationship is a single category FK (flat categories); inventory is one-to-one with product; reservation and movement rows reference product and actor. Carts belong to a user with one active cart enforced by a partial unique index; cart items have a unique product per cart and reference products. Cart lines cascade on cart deletion; product and user deletion are restricted. Order/permission relationships above remain conceptual. Inventory movements are append-only. Retention and anonymization decisions for future personal data remain deferred.
