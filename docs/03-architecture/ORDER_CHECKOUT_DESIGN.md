# Order, checkout, inventory, and simulated payments

## Trust boundaries

The authenticated customer is resolved server-side. Checkout accepts only product UUID and quantity assertions, requires a customer-scoped `Idempotency-Key`, and compares the assertion to the current PostgreSQL cart. Product status, category availability, SKU/name/price, totals, inventory and customer identity are obtained from server-side state. Currency is currently USD; tax, discount and shipping are explicitly zero.

## Checkout transaction and idempotency

`checkout_idempotency` serializes duplicate keys using its unique `(customer_id, idempotency_key)` constraint and a row lock. The SHA-256 request fingerprint covers canonical product/quantity lines. Reusing a key with changed lines is a conflict. In a transaction, checkout locks the active cart, catalog rows and inventory rows in product UUID order; validates all products and stock; then inserts the order, immutable item snapshots, order-linked inventory reservations, ledger movements and a `PROCESSING` payment intent. Any error rolls the whole transaction back, including the temporary idempotency claim.

After commit, checkout calls only `SimulatedPaymentGateway`; no external provider or real charge is invoked. A second transaction records the simulator result, then confirms the order or marks payment failure and releases all reservations. The simulator outcome is controlled by `APP_PAYMENT_SIMULATOR_OUTCOME` (`SUCCESS`, `DECLINED`, `TIMEOUT`, `FAILURE`) for deterministic local testing. Retry with the same key resumes an unfinished intent or returns its persisted result. A failed order is not charged/retried by replay; a new checkout intent uses a new key.

## Reservation lifecycle and fulfillment

Inventory uses the existing PostgreSQL `inventory`, `inventory_reservations` and append-only `inventory_movements` tables. A confirmed order holds reserved stock. Cancellation before processing releases it once, writes a movement, and marks the simulated payment `VOIDED` (not a refund). ADMIN transition to `PROCESSING` consumes on-hand and reserved quantities once; ADMIN may then advance to `SHIPPED` and `DELIVERED`. Customers can view/cancel only their own eligible order; ADMIN order routes are role-protected.

Automatic expiry is deliberately deferred. A crash can leave a payment intent/order in PROCESSING and its reservation held; same-key retry is the current recovery path. An outbox, remote-provider unknown-outcome reconciliation, refunds, shipping, tax and production payment processing are outside scope.

## Schema and API

Flyway V4 adds orders, snapshots, simulated payments, idempotency, status history and order references/lifecycle states for V3 reservations. See `ADR-024` through `ADR-028` for state, locking, idempotency, payment and cancellation decisions.

- `POST /api/v1/checkout` (`Idempotency-Key`, `{items:[{productId,quantity}]}`)
- `GET /api/v1/orders`, `GET /api/v1/orders/{id}`, `GET /api/v1/orders/{id}/payment`
- `POST /api/v1/orders/{id}/cancel` (`{reason}`)
- ADMIN: `GET /api/v1/admin/orders`, `/summary`, `/{id}`, and `PATCH /{id}/status`

All mutation routes use the existing session/CSRF defenses. No client-supplied prices, totals, stock or customer IDs are accepted.
