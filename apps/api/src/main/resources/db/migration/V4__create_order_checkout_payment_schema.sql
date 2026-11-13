CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    cart_id UUID REFERENCES carts(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL,
    payment_status VARCHAR(24) NOT NULL,
    subtotal NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    shipping_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    cancellation_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customer_orders_customer_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ck_customer_orders_status CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','PAYMENT_FAILED')),
    CONSTRAINT ck_customer_orders_payment_status CHECK (payment_status IN ('PROCESSING','SUCCEEDED','DECLINED','TIMED_OUT','FAILED','VOIDED')),
    CONSTRAINT ck_customer_orders_amounts CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal
        AND tax_amount >= 0 AND shipping_amount >= 0 AND total_amount >= 0
        AND total_amount = subtotal - discount_amount + tax_amount + shipping_amount
    ),
    CONSTRAINT ck_customer_orders_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_customer_orders_idempotency_key CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    CONSTRAINT ck_customer_orders_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_customer_orders_cancel_reason CHECK ((status = 'CANCELLED' AND cancellation_reason IS NOT NULL) OR status <> 'CANCELLED'),
    CONSTRAINT ck_customer_orders_payment_lifecycle CHECK (
        (status = 'PENDING_PAYMENT' AND payment_status = 'PROCESSING')
        OR (status IN ('CONFIRMED','PROCESSING','SHIPPED','DELIVERED') AND payment_status = 'SUCCEEDED')
        OR (status = 'PAYMENT_FAILED' AND payment_status IN ('DECLINED','TIMED_OUT','FAILED'))
        OR (status = 'CANCELLED' AND payment_status = 'VOIDED')
    )
);
CREATE INDEX ix_customer_orders_customer_created ON customer_orders(customer_id, created_at DESC, id DESC);
CREATE INDEX ix_customer_orders_status_created ON customer_orders(status, created_at DESC, id DESC);
CREATE INDEX ix_customer_orders_number ON customer_orders(order_number);
CREATE UNIQUE INDEX uq_customer_orders_pending_cart ON customer_orders(cart_id)
    WHERE cart_id IS NOT NULL AND status = 'PENDING_PAYMENT';

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    sku_snapshot VARCHAR(64) NOT NULL,
    product_name_snapshot VARCHAR(180) NOT NULL,
    unit_price NUMERIC(14,2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 9999),
    CONSTRAINT ck_order_items_line_total CHECK (line_total = unit_price * quantity),
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id)
);
CREATE INDEX ix_order_items_order ON order_items(order_id, created_at, id);
CREATE INDEX ix_order_items_product ON order_items(product_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES customer_orders(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL DEFAULT 'SIMULATED',
    provider_reference VARCHAR(64) UNIQUE,
    status VARCHAR(24) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    failure_category VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payments_provider CHECK (provider = 'SIMULATED'),
    CONSTRAINT ck_payments_status CHECK (status IN ('PROCESSING','SUCCEEDED','DECLINED','TIMED_OUT','FAILED','VOIDED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_payments_failure CHECK (
        (status IN ('DECLINED','TIMED_OUT','FAILED') AND failure_category IS NOT NULL)
        OR (status NOT IN ('DECLINED','TIMED_OUT','FAILED') AND failure_category IS NULL)
    )
);
CREATE INDEX ix_payments_status_updated ON payments(status, updated_at);

CREATE TABLE checkout_idempotency (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    order_id UUID UNIQUE REFERENCES customer_orders(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_checkout_idempotency_customer_key UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ck_checkout_idempotency_key CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    CONSTRAINT ck_checkout_idempotency_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id) ON DELETE RESTRICT,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_history_from_status CHECK (from_status IS NULL OR from_status IN ('PENDING_PAYMENT','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','PAYMENT_FAILED')),
    CONSTRAINT ck_order_history_to_status CHECK (to_status IN ('PENDING_PAYMENT','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','PAYMENT_FAILED'))
);
CREATE INDEX ix_order_history_order_time ON order_status_history(order_id, created_at, id);

CREATE FUNCTION reject_order_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'order snapshots and status history are immutable';
END;
$$;
CREATE TRIGGER trg_order_items_immutable
    BEFORE UPDATE OR DELETE ON order_items
    FOR EACH ROW EXECUTE FUNCTION reject_order_history_mutation();
CREATE TRIGGER trg_order_status_history_immutable
    BEFORE UPDATE OR DELETE ON order_status_history
    FOR EACH ROW EXECUTE FUNCTION reject_order_history_mutation();

ALTER TABLE inventory_reservations
    ADD COLUMN order_id UUID REFERENCES customer_orders(id) ON DELETE RESTRICT,
    ADD COLUMN committed_at TIMESTAMPTZ;
ALTER TABLE inventory_reservations DROP CONSTRAINT ck_reservation_status;
ALTER TABLE inventory_reservations ADD CONSTRAINT ck_reservation_status
    CHECK (status IN ('RESERVED', 'RELEASED', 'COMMITTED'));
ALTER TABLE inventory_reservations DROP CONSTRAINT ck_reservation_release_time;
ALTER TABLE inventory_reservations ADD CONSTRAINT ck_reservation_lifecycle_time CHECK (
    (status = 'RESERVED' AND released_at IS NULL AND committed_at IS NULL)
    OR (status = 'RELEASED' AND released_at IS NOT NULL AND committed_at IS NULL)
    OR (status = 'COMMITTED' AND released_at IS NULL AND committed_at IS NOT NULL)
);
CREATE UNIQUE INDEX uq_order_reservation_order_product
    ON inventory_reservations(order_id, product_id) WHERE order_id IS NOT NULL;
CREATE INDEX ix_order_reservations_order_status ON inventory_reservations(order_id, status) WHERE order_id IS NOT NULL;

ALTER TABLE inventory_movements DROP CONSTRAINT ck_movement_type;
ALTER TABLE inventory_movements ADD CONSTRAINT ck_movement_type CHECK (
    movement_type IN ('INITIAL_STOCK','STOCK_IN','STOCK_OUT','CORRECTION','RESERVATION','RESERVATION_RELEASE',
                      'ORDER_RESERVATION','ORDER_RESERVATION_RELEASE','ORDER_COMMIT')
);
