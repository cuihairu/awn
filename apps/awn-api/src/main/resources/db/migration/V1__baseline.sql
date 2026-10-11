-- =============================================================================
-- V1__baseline.sql — Phase 00 基线
-- =============================================================================
-- 只做全局基础设施，不建业务表；库存 / 订单 / 支付等业务表自 Phase 01 起
-- 按模块逐版本追加（见本目录 README.md 的迁移约定）。
-- =============================================================================

-- 搜索辅助（活动标题、专场模糊检索，Phase 02+ 使用）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- updated_at 维护函数：后续业务表统一挂触发器，禁止业务代码手写 updated_at
CREATE OR REPLACE FUNCTION awn_set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 交易库统一使用 UTC（TIMESTAMPTZ）；显式声明，防止实例级时区漂移
SET TIME ZONE 'UTC';
