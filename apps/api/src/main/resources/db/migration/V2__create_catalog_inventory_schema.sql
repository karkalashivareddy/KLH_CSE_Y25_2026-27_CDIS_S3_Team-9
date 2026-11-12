CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL UNIQUE,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_categories_slug CHECK (slug = lower(slug) AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    short_description VARCHAR(500),
    description TEXT,
    price NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    image_url VARCHAR(2048),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_currency CHECK (currency = 'USD'),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_products_sku CHECK (sku = upper(sku)),
    CONSTRAINT ck_products_slug CHECK (slug = lower(slug) AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE INDEX ix_products_public_category ON products(category_id, status, name, id);
CREATE INDEX ix_products_public_created ON products(status, created_at DESC, id);
CREATE INDEX ix_products_name_lower ON products(lower(name));

CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE REFERENCES products(id) ON DELETE RESTRICT,
    quantity_on_hand BIGINT NOT NULL DEFAULT 0,
    quantity_reserved BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (quantity_reserved >= 0),
    CONSTRAINT ck_inventory_reserved_le_on_hand CHECK (quantity_reserved <= quantity_on_hand)
);

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('RESERVED', 'RELEASED')),
    CONSTRAINT ck_reservation_release_time CHECK ((status = 'RESERVED' AND released_at IS NULL) OR (status = 'RELEASED' AND released_at IS NOT NULL))
);
CREATE INDEX ix_reservations_product_status ON inventory_reservations(product_id, status);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    movement_type VARCHAR(32) NOT NULL,
    quantity_delta BIGINT NOT NULL,
    reserved_delta BIGINT NOT NULL,
    previous_quantity BIGINT NOT NULL,
    new_quantity BIGINT NOT NULL,
    previous_reserved BIGINT NOT NULL,
    new_reserved BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reference_id UUID,
    performed_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_movement_type CHECK (movement_type IN ('INITIAL_STOCK', 'STOCK_IN', 'STOCK_OUT', 'CORRECTION', 'RESERVATION', 'RESERVATION_RELEASE')),
    CONSTRAINT ck_movement_snapshot CHECK (previous_quantity >= 0 AND new_quantity >= 0 AND previous_reserved >= 0 AND new_reserved >= 0 AND new_reserved <= new_quantity),
    CONSTRAINT ck_movement_delta CHECK (new_quantity - previous_quantity = quantity_delta AND new_reserved - previous_reserved = reserved_delta)
);
CREATE INDEX ix_movements_product_time ON inventory_movements(product_id, created_at DESC, id DESC);

CREATE FUNCTION reject_inventory_movement_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'inventory movements are append-only';
END;
$$;
CREATE TRIGGER trg_inventory_movements_immutable
    BEFORE UPDATE OR DELETE ON inventory_movements
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_movement_mutation();
