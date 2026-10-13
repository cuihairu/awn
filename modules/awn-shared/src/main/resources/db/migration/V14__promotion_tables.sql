-- V14__promotion_tables.sql — promotion 模块：优惠券模板与券实例
CREATE TABLE coupon_templates (
    id             BIGSERIAL PRIMARY KEY,
    template_no    VARCHAR(64)  NOT NULL UNIQUE,
    title          VARCHAR(200) NOT NULL,
    discount_kind  VARCHAR(32)  NOT NULL,
    discount_value BIGINT       NOT NULL,
    min_spend      BIGINT       NOT NULL DEFAULT 0,
    valid_from     TIMESTAMPTZ,
    valid_until    TIMESTAMPTZ,
    total_quota    INTEGER      NOT NULL,
    issued_count   INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_coupon_template_discount CHECK (discount_value > 0),
    CONSTRAINT ck_coupon_template_quota CHECK (total_quota > 0 AND issued_count >= 0 AND issued_count <= total_quota)
);

CREATE TABLE coupons (
    id          BIGSERIAL PRIMARY KEY,
    coupon_no   VARCHAR(64) NOT NULL UNIQUE,
    template_id BIGINT      NOT NULL REFERENCES coupon_templates (id),
    user_id     BIGINT      NOT NULL,
    status      VARCHAR(32) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at     TIMESTAMPTZ,
    CONSTRAINT ck_coupon_status CHECK (status IN ('UNUSED', 'USED')),
    CONSTRAINT uq_coupon_per_user UNIQUE (template_id, user_id)
);
CREATE INDEX idx_coupons_user ON coupons (user_id);
