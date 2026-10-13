-- =============================================================================
-- V12__trading_role.sql — 交易角色最小权限化
-- =============================================================================
-- 设计依据：docs/architecture/intelligence-isolation.md（交易角色最小权限化待做）、
--           docs/deployment/production.md（生产收紧路径：受限 awn_app）。
-- 现状问题：应用以实例引导超级用户连接，任何进程都能读写情报 schema。
-- 本迁移建立受限交易角色 awn_trading：
--   1. 只拥有 public（交易表组）的读写权限，不含 CREATE（不能新增对象）；
--   2. 对 intelligence schema 零权限（REVOKE 显式声明，便于审计）；
--   3. 不授予 SUPERUSER / CREATEROLE / CREATEDB。
-- 迁移以应用账号（当前为实例引导超级用户）执行，故可 CREATE ROLE。
-- 本脚本幂等：重复执行（如手工补授权）不报错。
-- 边界性质与 awn_intel 相同——权限边界，不是物理隔离；
-- 验证见 TradingRoleIsolationTests。
-- =============================================================================

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'awn_trading') THEN
        CREATE ROLE awn_trading LOGIN PASSWORD 'change-me-trading-password';
    END IF;
END $$;

DO $$ BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO awn_trading', current_database());
END $$;

-- 交易表组（public schema）：完整读写 + 序列 + 触发器函数，不含 CREATE
GRANT USAGE ON SCHEMA public TO awn_trading;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO awn_trading;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO awn_trading;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO awn_trading;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO awn_trading;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO awn_trading;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO awn_trading;

-- 情报 schema：显式零权限（默认无授权；REVOKE 使后续收紧有据可查）
REVOKE ALL ON SCHEMA intelligence FROM awn_trading;
REVOKE ALL ON ALL TABLES IN SCHEMA intelligence FROM awn_trading;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA intelligence FROM awn_trading;
ALTER DEFAULT PRIVILEGES IN SCHEMA intelligence REVOKE ALL ON TABLES FROM awn_trading;
ALTER DEFAULT PRIVILEGES IN SCHEMA intelligence REVOKE ALL ON SEQUENCES FROM awn_trading;
