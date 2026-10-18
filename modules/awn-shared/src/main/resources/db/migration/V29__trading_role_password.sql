-- =============================================================================
-- V29__trading_role_password.sql — 交易角色密码改由部署侧配置
-- =============================================================================
-- 目的（docs/design/dual-datasource-split-plan.md S4 第 3 步）：V12 以固定占位密码
-- 建 awn_trading；S4 将 api / worker 运行账号切到 awn_trading 前，密码必须可由
-- 部署注入，不能再沿用源码常量。
-- 密码经 Flyway placeholder trading_role_password 注入：由 awn-api 的
-- FlywayPlaceholderConfig 从环境变量 AWN_TRADING_DB_PASSWORD 读取（默认与 V12
-- 建号密码一致，幂等重放安全）。占位符不能写在 application.yml 的
-- spring.flyway.placeholders 里：Spring 绑定 Map 值不解析嵌套占位符。
-- 注意 1：Flyway 对注释行内的占位符同样做替换，本文件头注释不得出现
-- AWN_TRADING_DB_PASSWORD 的占位符写法，只能写变量名。
-- 注意 2：已应用迁移不受影响；若环境曾手工 ALTER ROLE 改过 awn_trading 密码，
-- 应用启动到本迁移时会按配置值重设，部署时设置 AWN_TRADING_DB_PASSWORD 与
-- 运行账号保持一致即可。
-- =============================================================================

ALTER ROLE awn_trading LOGIN PASSWORD '${trading_role_password}';
