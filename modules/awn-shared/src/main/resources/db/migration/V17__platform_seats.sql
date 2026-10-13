-- 平台座位型票品：平台自营逐座交易的独立模型。
-- trading-model.md 关系要点 3：禁止复用挂牌票品 ticket_units（其外键挂 listings，
-- 语义是「卖家的票」）；本表是「平台的座位」，挂场次与商品编码（字符串松耦合，与库存池同口径）。
-- 逐座状态机：IN_STOCK -> RESERVED -> SOLD -> DELIVERED；INVALID 为作废终态。
CREATE TABLE platform_seats (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seat_no      VARCHAR(64) NOT NULL UNIQUE,
    product_code VARCHAR(64) NOT NULL,
    session_code VARCHAR(64) NOT NULL,
    section_name VARCHAR(64) NOT NULL,
    row_label    VARCHAR(16) NOT NULL,
    seat_label   VARCHAR(16) NOT NULL,
    order_no     VARCHAR(64),
    status       VARCHAR(16) NOT NULL DEFAULT 'IN_STOCK' CHECK (status IN (
                    'IN_STOCK', 'RESERVED', 'SOLD', 'DELIVERED', 'INVALID')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 物理座位唯一：同一场次同一位置只能属于一个商品（一个座位不能卖两次）
    CONSTRAINT uq_platform_seats_position UNIQUE (session_code, section_name, row_label, seat_label)
);
CREATE INDEX ix_platform_seats_product ON platform_seats (product_code, status);
CREATE INDEX ix_platform_seats_session ON platform_seats (session_code, status);
