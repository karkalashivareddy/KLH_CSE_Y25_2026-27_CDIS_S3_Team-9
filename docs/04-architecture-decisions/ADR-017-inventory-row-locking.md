# ADR-017: Pessimistic row locking for inventory mutations

- **Status:** Accepted for Phase 3
- **Date:** 2026-11-12
- **Context:** inventory mutations must serialize at the product inventory row so concurrent reservation attempts cannot consume the same available units.
- **Options:** optimistic version/retry; conditional atomic SQL updates; PostgreSQL `SELECT FOR UPDATE` pessimistic lock.
- **Decision:** each inventory mutation runs in one Spring transaction and obtains a PostgreSQL pessimistic write lock on the product's inventory row before checking or changing on-hand/reserved quantities. The same transaction writes the reservation row (where applicable) and immutable stock-movement row. A reservation is admitted iff `quantity_on_hand - quantity_reserved >= requested_quantity`. Database check constraints remain a second line of defense. `@Version` also detects stale ORM updates.
- **Release path:** acquire locks in consistent order (inventory row, then reservation row), verify reservation is still RESERVED and belongs to the product, decrease reserved quantity exactly once, mark it RELEASED, and append a movement. Duplicate release conflicts without changing stock.
- **Failure semantics:** insufficient stock and duplicate/invalid lifecycle actions map to HTTP 409; transaction rollback leaves inventory, reservation, and movement unchanged.
- **Trade-offs:** row lock contention serializes writes for one SKU but allows independent SKUs to progress concurrently. This is appropriate for current single-database scale. Re-evaluate atomic updates/partitioning only with measured contention.
- **Validation:** PostgreSQL Testcontainers test synchronizes concurrent requests at a barrier against a final-unit balance and verifies exactly one succeeds, no negative availability, consistent reservations and ledger entries. No sleeps are used for coordination.
