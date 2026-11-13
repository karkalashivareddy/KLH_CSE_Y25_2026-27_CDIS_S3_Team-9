CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);
CREATE UNIQUE INDEX uq_carts_one_active_per_user ON carts(user_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_carts_user_status ON carts(user_id, status);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 9999),
    CONSTRAINT uq_cart_item_product UNIQUE (cart_id, product_id)
);
CREATE INDEX ix_cart_items_cart_created ON cart_items(cart_id, created_at, id);
CREATE INDEX ix_cart_items_product ON cart_items(product_id);
