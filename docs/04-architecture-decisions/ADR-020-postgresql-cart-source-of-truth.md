# ADR-020: PostgreSQL is the durable cart source of truth

- **Status:** Accepted for Phase 4
- **Context:** Customer carts must survive API restarts and remain transactionally consistent, while Redis is introduced for ephemeral shared sessions.
- **Decision:** Store active carts and cart lines in PostgreSQL through Flyway V3. Cart ownership is derived exclusively from the authenticated CUSTOMER principal. Redis contains no cart state and no cache is introduced.
- **Integrity:** A partial unique index allows only one ACTIVE cart per user. Cart items have unique `(cart_id, product_id)`, positive bounded quantities, and product/cart foreign keys. Product deletion is restricted; clearing removes lines but keeps an active cart.
- **Price and stock:** Responses join current product prices and current availability. These values are display-time values, not checkout commitments. Cart writes do not reserve inventory, change inventory tables, or create orders/payments.
- **Consequences:** Each cart read/mutation is database-backed. Cart recovery follows PostgreSQL backup/restore policy, not Redis session policy.
