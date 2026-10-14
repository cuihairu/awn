-- =============================================================================
-- V23__order_seat_lines.sql — 选座下单契约（todo 2D 座位图余项）
-- =============================================================================
-- 设计依据：docs/architecture/trading-model.md 关系要点 3（platform_seats 独立模型）。
-- 订单行第三条库存路径：座位型（seat_no 引用 platform_seats.seat_no），
-- 与数量型（pool_code + reservation_no）、凭证型（listing_no + unit_no）互斥。
-- 下单时逐座 IN_STOCK → RESERVED（守卫迁移与订单同事务，并发抢座只放行一单），
-- 取消 / 超时释放回 IN_STOCK，支付确认转 SOLD（inventory 模块状态机裁决）。
-- =============================================================================

ALTER TABLE order_items ADD COLUMN seat_no VARCHAR(64);

-- 三路径互斥：一行恰有一条库存路径（应用层 OrderLineCommand 先裁决，CHECK 兜底）
ALTER TABLE order_items DROP CONSTRAINT ck_order_item_source;
ALTER TABLE order_items ADD CONSTRAINT ck_order_item_source CHECK (
    (reservation_no IS NOT NULL AND listing_no IS NULL AND seat_no IS NULL)
    OR (reservation_no IS NULL AND listing_no IS NOT NULL AND unit_no IS NOT NULL AND seat_no IS NULL)
    OR (reservation_no IS NULL AND listing_no IS NULL AND unit_no IS NULL AND seat_no IS NOT NULL)
);

CREATE INDEX ix_order_items_seat ON order_items (seat_no);
