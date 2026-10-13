-- =============================================================================
-- V16__order_voucher_lines.sql — G2：凭证型订单行（挂牌票品）接入订单链路
-- =============================================================================
-- 设计依据：docs/architecture/trading-model.md G2。
-- 问题：挂牌（listings）与独立票品（ticket_units）均已实现，但订单链路只支持
--       数量型行（pool_code + reservation_no），第三方挂牌事实上不可交易。
-- 方案：订单行增加凭证型引用（listing_no + unit_no）。一行恰有一条库存路径——
--       数量型走 pool_code / reservation_no；凭证型走 listing_no / unit_no，
--       下单时把挂牌的票品从 IN_STOCK 预留为 RESERVED（与订单同事务）。
-- 边界：不新建表；票品与挂牌状态机仍属 marketplace 模块，订单侧只经 api 调用。
-- 验证：VoucherOrderModuleTests（下单/取消/支付确认 + 并发抢单 + 校验）。
-- =============================================================================

-- 两类行互斥，故两条路径的字段都必须可空；互斥性由下面的 CHECK 兜底
ALTER TABLE order_items ALTER COLUMN pool_code DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN reservation_no DROP NOT NULL;
ALTER TABLE order_items ADD COLUMN listing_no VARCHAR(64);
ALTER TABLE order_items ADD COLUMN unit_no VARCHAR(64);

-- 恰有一条库存路径（应用层 OrderLineCommand 先裁决，数据库 CHECK 兜底）
ALTER TABLE order_items ADD CONSTRAINT ck_order_item_source CHECK (
    (reservation_no IS NOT NULL AND listing_no IS NULL)
    OR (reservation_no IS NULL AND listing_no IS NOT NULL AND unit_no IS NOT NULL)
);

CREATE INDEX ix_order_items_listing ON order_items (listing_no);
