-- =============================================================================
-- V7__marketplace_fulfillment_tables.sql — Phase 05：卖家挂牌、独立票品、履约交付
-- =============================================================================
-- 设计依据：docs/architecture/domain-model.md（履约状态独立于订单/支付状态）、
--           docs/architecture/module-boundaries.md（marketplace 不得未经确认
--           创建平台可信库存；fulfillment 不得直接把待交付订单标记完成）。
-- =============================================================================

-- 卖家资料
CREATE TABLE sellers (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_no    VARCHAR(64) NOT NULL UNIQUE,
    user_id      BIGINT      NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    contact      VARCHAR(256),
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sellers_user ON sellers (user_id);

-- 供货审核：挂牌获得「平台保证」资质的唯一来源
CREATE TABLE supply_reviews (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_no    VARCHAR(64) NOT NULL UNIQUE,
    seller_id    BIGINT      NOT NULL REFERENCES sellers (id),
    product_code VARCHAR(64) NOT NULL,
    session_code VARCHAR(64),
    quantity     INTEGER     NOT NULL CHECK (quantity > 0),
    unit_price   BIGINT      NOT NULL CHECK (unit_price > 0),
    evidence_url TEXT,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewer     VARCHAR(64),
    reject_reason VARCHAR(256),
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_supply_reviews_seller ON supply_reviews (seller_id, status);

-- 挂牌：独立票品的售卖入口；platform_guaranteed 只能来自 APPROVED 供货审核
CREATE TABLE listings (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    listing_no         VARCHAR(64) NOT NULL UNIQUE,
    seller_id          BIGINT      NOT NULL REFERENCES sellers (id),
    supply_review_id   BIGINT      NOT NULL REFERENCES supply_reviews (id),
    product_code       VARCHAR(64) NOT NULL,
    session_code       VARCHAR(64),
    unit_price         BIGINT      NOT NULL CHECK (unit_price > 0),
    platform_guaranteed BOOLEAN    NOT NULL DEFAULT FALSE,
    status             VARCHAR(16) NOT NULL DEFAULT 'LISTED' CHECK (status IN ('LISTED', 'RESERVED', 'SOLD', 'DELISTED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_listings_seller ON listings (seller_id, status);
CREATE INDEX ix_listings_status ON listings (status, updated_at);

-- 独立票品状态：与挂牌解耦（票品有自己的生命周期与交付跟踪）
CREATE TABLE ticket_units (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_no    VARCHAR(64) NOT NULL UNIQUE,
    listing_id BIGINT      NOT NULL REFERENCES listings (id),
    seller_id  BIGINT      NOT NULL REFERENCES sellers (id),
    status     VARCHAR(16) NOT NULL DEFAULT 'IN_STOCK' CHECK (status IN (
                   'IN_STOCK', 'RESERVED', 'SOLD', 'DELIVERED', 'INVALID')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_units_listing ON ticket_units (listing_id, status);

-- 履约单：支付成功事件创建（PENDING_SELLER），完成需卖家交付 + 平台核验
CREATE TABLE fulfillments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fulfillment_no VARCHAR(64) NOT NULL UNIQUE,
    order_no       VARCHAR(64) NOT NULL UNIQUE,
    status         VARCHAR(24) NOT NULL DEFAULT 'PENDING_SELLER' CHECK (status IN (
                       'NOT_STARTED', 'PENDING_SELLER', 'PENDING_VERIFICATION',
                       'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED')),
    fail_reason    VARCHAR(256),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_fulfillments_status ON fulfillments (status, updated_at);

-- 交付记录：凭证 URL 仅允许白名单存储域（应用层校验）
CREATE TABLE delivery_records (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fulfillment_no VARCHAR(64) NOT NULL REFERENCES fulfillments (fulfillment_no),
    evidence_url   TEXT        NOT NULL,
    result         VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (result IN ('PENDING', 'PASSED', 'REJECTED')),
    note           VARCHAR(256),
    delivered_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at    TIMESTAMPTZ
);
CREATE INDEX ix_delivery_records_fulfillment ON delivery_records (fulfillment_no);

CREATE TRIGGER trg_sellers_updated_at BEFORE UPDATE ON sellers
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_supply_reviews_updated_at BEFORE UPDATE ON supply_reviews
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_listings_updated_at BEFORE UPDATE ON listings
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_ticket_units_updated_at BEFORE UPDATE ON ticket_units
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_fulfillments_updated_at BEFORE UPDATE ON fulfillments
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
