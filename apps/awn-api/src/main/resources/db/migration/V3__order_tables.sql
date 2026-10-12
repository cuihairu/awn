-- =============================================================================
-- V3__order_tables.sql — Phase 03 第二片：订单、明细快照、Outbox
-- =============================================================================
-- 设计依据：docs/architecture/domain-model.md（订单快照、状态定义）、
--           docs/architecture/payment-state-machine.md（订单状态机）、
--           docs/architecture/async-outbox.md（outbox_messages 结构）。
-- 金额一律最小货币单位整数（BIGINT），禁止浮点。
-- =============================================================================

CREATE TABLE orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no        VARCHAR(64)  NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_hash    VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    status          VARCHAR(24)  NOT NULL CHECK (status IN (
                        'PENDING_PAYMENT', 'PAID', 'FULFILLING', 'FULFILLED',
                        'REFUND_PENDING', 'REFUNDED', 'CANCELLED', 'EXPIRED')),
    currency        VARCHAR(3)   NOT NULL,
    total_amount    BIGINT       NOT NULL CHECK (total_amount >= 0),
    discount_amount BIGINT       NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    fee_amount      BIGINT       NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    pay_amount      BIGINT       NOT NULL CHECK (pay_amount >= 0),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_orders_user   ON orders (user_id, created_at);
CREATE INDEX ix_orders_status ON orders (status, expires_at);

-- 订单明细快照：下单时的成交事实，创建后不可变（见下方 immutable 触发器）
CREATE TABLE order_items (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id                 BIGINT      NOT NULL REFERENCES orders (id),
    pool_code                VARCHAR(64) NOT NULL,
    product_id               VARCHAR(64) NOT NULL,
    product_name_snapshot    VARCHAR(200) NOT NULL,
    session_id               VARCHAR(64),
    ticket_category_snapshot VARCHAR(64),
    quantity                 INT         NOT NULL CHECK (quantity > 0),
    unit_price               BIGINT      NOT NULL CHECK (unit_price >= 0),
    discount_amount          BIGINT      NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    fee_amount               BIGINT      NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    line_total               BIGINT      NOT NULL CHECK (line_total >= 0),
    reservation_no           VARCHAR(64) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_order_items_order ON order_items (order_id);

-- 快照不可变：任何 UPDATE 直接由数据库拒绝
CREATE OR REPLACE FUNCTION awn_forbid_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'rows in % are immutable', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_order_items_immutable BEFORE UPDATE ON order_items
    FOR EACH ROW EXECUTE FUNCTION awn_forbid_update();

-- 交易关键事件 Outbox：与业务数据同事务写入（docs/architecture/async-outbox.md）
CREATE TABLE outbox_messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type  VARCHAR(40)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ
);

CREATE INDEX ix_outbox_pending ON outbox_messages (status, next_attempt_at);

CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
