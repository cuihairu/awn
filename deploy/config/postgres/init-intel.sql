-- 情报库隔离初始化：独立 schema + 最小权限角色（应用侧使用 awn_intel 用户）
-- 约束（docs/architecture/intelligence-isolation.md）：
--   awn_intel 只允许访问 intelligence schema，禁止写入任何交易表。

CREATE SCHEMA IF NOT EXISTS intelligence;

CREATE ROLE awn_intel LOGIN PASSWORD 'change-me-intel-password';

GRANT USAGE ON SCHEMA intelligence TO awn_intel;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA intelligence TO awn_intel;
ALTER DEFAULT PRIVILEGES IN SCHEMA intelligence
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO awn_intel;

-- 显式撤销交易库权限（默认角色仅拥有 intelligence schema 的权限，
-- 此处仅作显式声明，便于审计）
REVOKE ALL ON SCHEMA public FROM awn_intel;
