-- 供货审核额度消耗：platform_guaranteed 挂牌时累加，上限为审核 quantity。
-- CHECK 约束是最后一道防线（consumed <= quantity 且计数非负）；
-- 并发守卫由 SupplyReviewRepository 的条件 UPDATE 裁决（与库存计数器同款）。
ALTER TABLE supply_reviews
    ADD COLUMN consumed_quantity INTEGER NOT NULL DEFAULT 0
    CHECK (consumed_quantity >= 0 AND consumed_quantity <= quantity);
