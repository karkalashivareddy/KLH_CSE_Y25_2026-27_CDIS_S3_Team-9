# Entity relationships

**Purpose/scope:** conceptual relationship map only; no deployed schema. See [database design](DATABASE_DESIGN.md).

```mermaid
erDiagram
  USER ||--o{ CART : owns
  USER ||--o{ ORDER : places
  USER ||--o{ ADDRESS : saves
  USER }o--o{ ROLE : assigned
  ROLE }o--o{ PERMISSION : grants
  CATEGORY }o--o{ PRODUCT : classifies
  PRODUCT ||--o{ INVENTORY : stocked
  CART ||--|{ CART_ITEM : contains
  PRODUCT ||--o{ CART_ITEM : selected
  ORDER ||--|{ ORDER_ITEM : snapshots
  PRODUCT ||--o{ ORDER_ITEM : referenced
  ORDER ||--o{ PAYMENT_SIMULATION : simulates
  USER ||--o{ AUDIT_EVENT : acts
  INCIDENT ||--o{ RECOVERY_EXECUTION : tracks
  CLOUD_RESOURCE ||--o{ COST_RECORD : incurs
```

Addresses may be copied into an order snapshot to preserve shipping history; avoid mutable address joins changing completed-order meaning. `USER`↔`ROLE`, `ROLE`↔`PERMISSION`, and `CATEGORY`↔`PRODUCT` need join entities/constraints. Decisions on cascade deletion, retention and anonymization remain deferred. Validation: compare each relationship with acceptance requirements before migrations.
