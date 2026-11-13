# ADR-026: Database-backed, customer-scoped checkout idempotency

- **Status:** Accepted for Phase 5
- **Context:** Network retries and concurrent duplicate requests must not create another order, reservation or logical payment.
- **Decision:** Require `Idempotency-Key` on checkout. A `checkout_idempotency` row has a unique `(customer_id, idempotency_key)`, canonical SHA-256 hash of product IDs and requested quantities, and resulting order ID. `INSERT ... ON CONFLICT` followed by `SELECT ... FOR UPDATE` serializes same-key requests. A hash mismatch returns 409. The order also has a unique customer/key constraint.
- **Payload:** Client sends product IDs and quantities only as a basket-consistency assertion; server loads the PostgreSQL cart and requires an exact match. Client prices, names, totals and customer ID are not accepted. Retries with the original key/payload return or resume the same order/payment; different payload with the key conflicts.
- **Failure semantics:** Pre-order validation/reservation failures roll back the idempotency claim, so a corrected/replenished cart can retry with that key. Once an order exists, its logical result is permanent for that key; a PAYMENT_FAILED order is not charged again by replay. A new user intent requires a new key.
