-- =============================================================================
-- V6__payment_tables.sql — Phase 04：支付、退款、Inbox
-- =============================================================================
-- 设计依据：docs/architecture/payment-state-machine.md（支付/退款状态、幂等要求）、
--           docs/architecture/async-outbox.md（inbox_messages 结构）。
-- 金额一律最小货币单位整数（BIGINT），禁止浮点。
-- =============================================================================

-- 支付单：状态独立于订单状态（docs/architecture/payment-state-machine.md）
CREATE TABLE payments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_no     VARCHAR(64) NOT NULL UNIQUE,
    order_no       VARCHAR(64) NOT NULL REFERENCES orders (order_no),
    channel        VARCHAR(32) NOT NULL,
    channel_txn_no VARCHAR(128),
    status         VARCHAR(24) NOT NULL DEFAULT 'CREATED' CHECK (status IN (
                       'CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN',
                       'REFUNDING', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    currency       VARCHAR(3)  NOT NULL,
    amount         BIGINT      NOT NULL CHECK (amount >= 0),
    paid_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 渠道交易号唯一：同一渠道交易只能对应一张支付单（回调去重的数据库兜底）
CREATE UNIQUE INDEX ux_payments_channel_txn ON payments (channel, channel_txn_no)
    WHERE channel_txn_no IS NOT NULL;
CREATE INDEX ix_payments_order  ON payments (order_no);
CREATE INDEX ix_payments_status ON payments (status, updated_at);

-- 退款单：独立退款编号、渠道退款编号、幂等键（docs/architecture/payment-state-machine.md）
CREATE TABLE refunds (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    refund_no         VARCHAR(64) NOT NULL UNIQUE,
    payment_no        VARCHAR(64) NOT NULL REFERENCES payments (payment_no),
    order_no          VARCHAR(64) NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL UNIQUE,
    channel_refund_no VARCHAR(128),
    status            VARCHAR(16) NOT NULL DEFAULT 'CREATED' CHECK (status IN (
                          'CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    currency          VARCHAR(3)  NOT NULL,
    amount            BIGINT      NOT NULL CHECK (amount > 0),
    reason            VARCHAR(256),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_refunds_channel_refund ON refunds (channel_refund_no)
    WHERE channel_refund_no IS NOT NULL;
CREATE INDEX ix_refunds_payment ON refunds (payment_no);
CREATE INDEX ix_refunds_status  ON refunds (status, updated_at);

-- Inbox：消费端去重（consumer + message_id 唯一），同一事件对同一消费者只生效一次
CREATE TABLE inbox_messages (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    consumer     VARCHAR(64) NOT NULL,
    message_id   BIGINT      NOT NULL REFERENCES outbox_messages (id),
    event_type   VARCHAR(60) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (consumer, message_id)
);

CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_refunds_updated_at BEFORE UPDATE ON refunds
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
