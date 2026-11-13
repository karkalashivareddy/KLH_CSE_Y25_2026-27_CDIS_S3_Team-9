# ADR-021: Serialize cart mutations with a PostgreSQL cart-row lock

- **Status:** Accepted for Phase 4
- **Context:** Concurrent requests must not create duplicate product lines or overwrite one another unpredictably.
- **Decision:** Every mutation first atomically creates-or-finds the unique ACTIVE cart and locks its row with `SELECT ... FOR UPDATE`. It then reads/inserts/updates/deletes lines in the same transaction. Database uniqueness on `(cart_id, product_id)` and the partial unique active-cart index are final integrity barriers.
- **Rejected:** JPA optimistic versioning is unnecessary for this single aggregate; portable check-then-insert without a unique index is race-prone; Redis locks add a second coordination system without benefit.
- **Conflict behavior:** Invalid request quantities return 400; unavailable/newly inactive products return 404; database integrity conflicts are mapped to safe 409 responses. Updates and removes are owner-scoped and a missing/not-owned line has the same 404 response.
- **Verification:** PostgreSQL barrier-synchronized tests race first-cart creation and same-product adds; update/remove races and forced transaction rollback are tested. The expected final row count and quantities are asserted.
