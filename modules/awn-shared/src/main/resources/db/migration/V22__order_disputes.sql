-- =============================================================================
-- V22__order_disputes.sql — P05 争议处理（买卖双方争议受理与裁决）
-- =============================================================================
-- 设计依据：docs/architecture/dispute-handling.md。
-- 买家按订单开争议（订单 PAID/FULFILLING/FULFILLED 才可开），卖家归属在开单时
-- 从凭证型订单行解析并快照（listing_no/unit_no/seller_id），不随挂牌变动回写。
-- 同订单至多一张在途争议：应用层判重 + 部分唯一索引兜底并发重复开单。
-- 裁决只记录结论，不动钱：退款执行走支付模块既有退款流，重新交付走履约面。
-- =============================================================================

CREATE TABLE order_disputes (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispute_no           VARCHAR(64) NOT NULL UNIQUE,
    order_no             VARCHAR(64) NOT NULL REFERENCES orders (order_no),
    opened_by            BIGINT      NOT NULL,
    seller_id            BIGINT,
    listing_no           VARCHAR(64),
    unit_no              VARCHAR(64),
    reason               VARCHAR(32) NOT NULL
                         CHECK (reason IN ('NOT_DELIVERED', 'TICKET_INVALID',
                                           'DESCRIPTION_MISMATCH', 'OTHER')),
    description          TEXT        NOT NULL,
    evidence_url         TEXT,
    requested_resolution VARCHAR(16) NOT NULL
                         CHECK (requested_resolution IN ('REFUND', 'REDELIVER')),
    requested_amount     BIGINT,
    status               VARCHAR(16) NOT NULL DEFAULT 'OPEN'
                         CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'WITHDRAWN', 'RESOLVED')),
    resolution           VARCHAR(32)
                         CHECK (resolution IN ('REFUND_APPROVED', 'REDELIVER_APPROVED', 'REJECTED')),
    resolution_note      TEXT,
    decided_by           VARCHAR(64),
    decided_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 同订单至多一张在途争议（OPEN/UNDER_REVIEW），终态不占位
CREATE UNIQUE INDEX ux_order_disputes_open ON order_disputes (order_no)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');

-- 管理面列表：最近争议（新→旧）
CREATE INDEX ix_order_disputes_created ON order_disputes (created_at DESC, id DESC);
-- 按订单查争议（买家面）
CREATE INDEX ix_order_disputes_order ON order_disputes (order_no, created_at DESC);

-- 争议单随状态迁移更新时间戳
CREATE TRIGGER trg_order_disputes_updated_at BEFORE UPDATE ON order_disputes
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
