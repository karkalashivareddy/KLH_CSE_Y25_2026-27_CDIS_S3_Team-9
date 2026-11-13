-- Phase 6: transactional outbox, idempotent consumer inbox and inspectable
-- downstream projections. All rows are written in the same database transaction
-- as the business change they describe.

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error_category VARCHAR(64),
    correlation_id UUID,
    causation_id UUID,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    CONSTRAINT ck_outbox_event_type CHECK (event_type IN (
        'OrderPlaced','OrderPaymentSucceeded','OrderPaymentFailed','OrderCancelled',
        'OrderProcessingStarted','OrderShipped','OrderDelivered')),
    CONSTRAINT ck_outbox_version CHECK (event_version >= 1),
    CONSTRAINT ck_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_lease CHECK (
        (status = 'PROCESSING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_owner IS NULL AND lease_expires_at IS NULL)),
    CONSTRAINT ck_outbox_published CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)),
    CONSTRAINT ck_outbox_aggregate CHECK (aggregate_type IN ('CustomerOrder')),
    CONSTRAINT ck_outbox_error CHECK (last_error_category IS NULL OR last_error_category ~ '^[A-Z0-9_]{1,64}$')
);
CREATE INDEX ix_outbox_due ON outbox_events(next_attempt_at, created_at, id)
    WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX ix_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX ix_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id, occurred_at);
CREATE INDEX ix_outbox_event_type ON outbox_events(event_type, occurred_at);

-- An inbox row is inserted in the same transaction as the consumer's durable
-- effect. The primary key makes duplicate delivery a no-op.
CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer_name),
    CONSTRAINT ck_processed_events_outcome CHECK (outcome IN ('PROCESSED','SKIPPED')),
    CONSTRAINT ck_processed_events_consumer CHECK (consumer_name ~ '^[a-z0-9.-]{1,128}$')
);

CREATE TABLE in_app_notifications (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    order_id UUID REFERENCES customer_orders(id) ON DELETE SET NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_event_customer UNIQUE (event_id, customer_id),
    CONSTRAINT ck_notification_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_notification_body CHECK (length(btrim(body)) > 0)
);
CREATE INDEX ix_notifications_customer_created ON in_app_notifications(customer_id, created_at DESC, id DESC);
CREATE INDEX ix_notifications_unread ON in_app_notifications(customer_id, created_at DESC) WHERE read_at IS NULL;

-- Reporting projection fed by the activity consumer. It is deliberately
-- separate from the authoritative order tables.
CREATE TABLE order_activity (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    order_status VARCHAR(24) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details JSONB,
    CONSTRAINT ck_order_activity_status CHECK (order_status IN (
        'PENDING_PAYMENT','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','PAYMENT_FAILED'))
);
CREATE INDEX ix_order_activity_order_time ON order_activity(order_id, occurred_at, id);
CREATE INDEX ix_order_activity_type_time ON order_activity(event_type, occurred_at);

-- Exhausted messages are quarantined for operator inspection. The original
-- message also lands in the broker dead-letter queue; this table adds a
-- durable, queryable and replayable representation.
CREATE TABLE dead_letter_messages (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    error_category VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUARANTINED',
    replayed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dead_letter_event_consumer UNIQUE (event_id, consumer_name),
    CONSTRAINT ck_dead_letter_status CHECK (status IN ('QUARANTINED','REPLAYED','DISCARDED')),
    CONSTRAINT ck_dead_letter_error CHECK (error_category ~ '^[A-Z0-9_]{1,64}$')
);
CREATE INDEX ix_dead_letter_status_created ON dead_letter_messages(status, created_at DESC, id DESC);

CREATE TABLE dead_letter_replays (
    id UUID PRIMARY KEY,
    dead_letter_id UUID NOT NULL REFERENCES dead_letter_messages(id) ON DELETE RESTRICT,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason VARCHAR(500) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dead_letter_replay_outcome CHECK (outcome IN ('PUBLISHED','PUBLISH_FAILED'))
);
CREATE INDEX ix_dead_letter_replays_message ON dead_letter_replays(dead_letter_id, created_at);
