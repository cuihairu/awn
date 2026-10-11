# Flyway 迁移约定

位置：`apps/awn-api/src/main/resources/db/migration/`（唯一迁移目录，交易库单一 schema）。

## 命名与版本

- 版本化迁移：`V{next}__{snake_case 描述}.sql`，如 `V2__inventory_tables.sql`。版本号全局递增，**不复用、不重排**。
- 已合并到 main 的迁移文件视为**不可变**：禁止修改内容（Flyway checksum 会拒绝启动）。修改一律追加新版本。
- 可重复对象（视图、函数重建）用 `R__` 前缀，命名如 `R__reporting_views.sql`。

## 执行权

- 迁移**只由 awn-api 进程执行**；awn-worker 的 `spring.flyway.enabled: false`，避免双进程并发迁移争锁。
- 情报库（`intelligence` schema）对象同样走本目录（脚本内显式 `CREATE ... IN SCHEMA intelligence`），其角色与权限由 `deploy/config/postgres/init-intel.sql` 保证——情报角色对交易表零权限。

## 内容规则

- 迁移在单事务内执行：不要使用 `CREATE INDEX CONCURRENTLY`（它不能在事务内运行）；超大表索引属 Phase 05 容量专项。
- 所有业务表：主键用 `BIGINT GENERATED ALWAYS AS IDENTITY`，时间字段用 `TIMESTAMPTZ`，`updated_at` 挂 `awn_set_updated_at()` 触发器（见 `V1__baseline.sql`）。
- 库存相关表变更必须同时说明对「原子条件 UPDATE 扣减」语句的影响（docs/architecture/inventory-consistency.md）。
- 金额一律 `BIGINT` 最小货币单位，禁止浮点类型。
