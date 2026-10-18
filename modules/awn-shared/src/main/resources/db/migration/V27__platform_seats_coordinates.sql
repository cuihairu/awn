-- =============================================================================
-- V27__platform_seats_coordinates.sql — S2 座位坐标契约先行（todo「3D 座位图」）
-- =============================================================================
-- 依据：docs/design/seatmap-3d-plan.md §3 S2，已代决（2026-10-18 巡检「契约先行」，
-- 记录见 docs/research/venue-seatmaps.md §8 #9，用户可推翻——回滚本迁移与对应
-- 契约改动即可）。只落契约面：坐标可选，null = 坐标缺失，前端回退现有网格
-- 渲染；不实现 3D、不接坐标消费逻辑、不加写入路径。
--
-- 本列是契约载体，不是数据承诺：写入路径（场馆方供给 / 采购 / 自绘编辑器导入）
-- 随挂起项拍板后另行落地，此前恒为 NULL。逐座坐标最终存法（本列 vs 独立坐标表，
-- venue-layout-schema.md §4.1）仍留待 L3 启动前定——改走坐标表则本列保持 NULL，
-- 公开契约（x/y 可空、null 回退）不受影响。
-- =============================================================================

ALTER TABLE platform_seats ADD COLUMN pos_x DOUBLE PRECISION;
ALTER TABLE platform_seats ADD COLUMN pos_y DOUBLE PRECISION;
