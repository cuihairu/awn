-- 情报库隔离初始化：独立 schema + 最小权限角色（应用侧使用 awn_intel 用户）
-- 约束（docs/architecture/intelligence-isolation.md）：
--   awn_intel 只允许访问 intelligence schema，禁止读写任何交易表。
-- 本脚本幂等，可重复执行（Flyway 先建表后，仍可补授权）。

CREATE SCHEMA IF NOT EXISTS intelligence;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'awn_intel') THEN
        CREATE ROLE awn_intel LOGIN PASSWORD 'change-me-intel-password';
    END IF;
END $$;

GRANT USAGE ON SCHEMA intelligence TO awn_intel;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA intelligence TO awn_intel;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA intelligence TO awn_intel;
ALTER DEFAULT PRIVILEGES IN SCHEMA intelligence
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO awn_intel;
ALTER DEFAULT PRIVILEGES IN SCHEMA intelligence
  GRANT USAGE, SELECT ON SEQUENCES TO awn_intel;

-- 显式撤销交易库权限：交易表组没有任何角色默认授权，
-- awn_intel 仅通过 intelligence schema 的授权访问数据（便于审计）。
REVOKE ALL ON SCHEMA public FROM awn_intel;
