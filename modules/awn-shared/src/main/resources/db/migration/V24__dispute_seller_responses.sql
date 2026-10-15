-- =============================================================================
-- V24__dispute_seller_responses.sql — 卖家申辩（追加留痕）
-- =============================================================================
-- 设计依据：docs/architecture/dispute-handling.md「卖家面（归属校验与申辩）」。
-- 申辩不覆盖前一条：一条申辩一张记录，管理员裁决时能看到全过程。
-- 归属校验（声明卖家 = 争议快照 seller_id）与在途守卫在应用层裁决，
-- 数据库只留外键与索引；纯平台库存订单（无卖家快照）不接受申辩。
-- =============================================================================

CREATE TABLE order_dispute_seller_responses (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispute_no  VARCHAR(64) NOT NULL REFERENCES order_disputes (dispute_no),
    seller_no   VARCHAR(64) NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 按争议读申辩（管理面裁决依据，提交时间正序）
CREATE INDEX ix_dispute_seller_responses_dispute
    ON order_dispute_seller_responses (dispute_no, created_at);
