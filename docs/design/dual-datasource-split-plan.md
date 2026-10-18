# 运行时账号收紧：双数据源 / JPA 单元拆分方案（待拍板，未实现）

解决审核表一 P1「API / Worker 使用 PostgreSQL 引导超级用户」（`todo.md` 审核整改条 表一 P1）。
本文件是**设计与分期方案**，未落任何实现；开工需用户拍板。可推翻：删掉本文件即回卡点。

## 卡点现状（证据，2026-10-19 复核）

| 事实 | 位置 |
|---|---|
| api / worker 均以实例引导超级用户连接 | `deploy/docker-compose.yml:57,88` `AWN_DB_USER: awn_app` |
| 两个应用各只有**一个** `datasource` 配置块，无第二数据源装配 | `apps/awn-api/src/main/resources/application.yml`、`apps/awn-worker/src/main/resources/application.yml` |
| 单一 JPA 扫描单元覆盖全部实体 | `apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java`（`@EntityScan` / `@EnableJpaRepositories` 均为 `site.awn`） |
| 情报实体硬映射独立 schema，却与交易表共用同一连接 | `modules/awn-intelligence/src/main/java/site/awn/intelligence/domain/RawObservation.java`（`@Table(name = "raw_observations", schema = "intelligence")`） |
| 情报服务使用默认事务管理器 | `modules/awn-intelligence/src/main/java/site/awn/intelligence/application/IntelligenceFacadeImpl.java`（5 处 `@Transactional`） |
| 受限角色已就绪，但应用未使用 | `V12__trading_role.sql`（`awn_trading`：public 读写、无 CREATE、intelligence 零权限）、`config/postgres/init-intel.sql`（`awn_intel`：intelligence 读写、public 零权限） |
| 角色验收为连接级 | `TradingRoleIsolationTests`、`IntelligenceIsolationTests`（均以 `DriverManager` 用受限账号直连断言） |

结论：把 `awn_db_user` 直接换成 `awn_trading` 会立刻让情报读写失败（零权限），换 `awn_intel` 则交易链路全断。必须先做运行时单元拆分。

## 关键可行性前提

- 情报表 DDL 在交易迁移轨内（V8 / V9 / V10），但**情报表的运行时访问**与迁移轨无关——迁移仍由一个高权限连接执行即可。
- 无任何交易模块反向依赖情报：`grep site.awn.intelligence` 只命中 `apps/awn-api/.../PublicIntelligenceController.java`、`apps/awn-worker/.../IntelligenceCollectionTask.java` 与测试。情报调用不嵌套在交易事务里，双事务管理器不会产生跨单元同事务需求。

## 方案对比

| 方案 | 做法 | 代价 | 结论 |
|---|---|---|---|
| **A（推荐）** | 双数据源 + 双 JPA 单元 + 双事务管理器；迁移轨不拆，仍由高权限连接执行；运行时交易走 `awn_trading`、情报走 `awn_intel` | 新增一个配置类 + 测试矩阵扩充 | 隔离目标达成，改动面最小 |
| B | 单数据源不动，把运行账号放宽为 `awn_intel`（需给它 public 读权限） | 情报可读交易表，隔离目标失效 | 否决 |
| C | 维持现状，仅收紧部署侧（管理员账号初始化 + Flyway 独立执行） | 运行账号仍是超管 | 仅作过渡兜底，不算修复 |

## 方案 A 分期

### S1：情报 JPA 单元独立（无行为变化）

- 新增配置类（建议置于 `modules/awn-shared` 或应用装配层）：
  - `intelEntityManagerFactory`：`@EntityScan` + `@EnableJpaRepositories` 限 `site.awn.intelligence`，绑定 intel 数据源；
  - `intelTransactionManager`；交易单元显式排除 `site.awn.intelligence`；
  - `IntelligenceFacadeImpl` 改 `@Transactional("intelTransactionManager")`（5 处同改）。
- 默认连接参数不变（两源同 URL 同账号），因此**行为完全不变**，仅验证装配正确。
- 验收：`AwnApiApplicationTests` / `ModularityTests` / `IntelligenceModuleTests` 全绿。
- 回滚：删配置类 + 还原事务注解，无数据变更。

### S2：受限账号接入集成测试（先补测试）

- 测试侧把 `IntelligenceIsolationTests.applyInitScriptAsOwner` 的建角色逻辑抽成共用测试工具，两个应用进程在测试容器内以 `awn_trading` / `awn_intel` 分别连接：
  - intel 数据源 → `awn_intel`；交易数据源 → `awn_trading`。
- 新增断言（先于 S3 实现）：
  1. 以 intel 连接跑一次 `collectAll` + 价格历史查询成功；
  2. 以交易连接跑一条下单→支付→回调→履约链路成功；
  3. 负向：交易模块任何写入经 intel 连接失败（现有权限测试已覆盖，补一条应用路径断言）。
- 验收：上述用例在 S1 之上变绿。
- 回滚：测试内连接参数还原。

### S3：迁移执行者与运行账号分离

- Flyway 只挂主数据源；主连接需要一个同时持有 `public` 与 `intelligence` 建对象权限的**非超管**迁移角色（新增迁移 V29 建 `awn_migration` 并授两 schema CREATE/USAGE，避免继续用超管跑迁移）。
- 交易单元保留 `ddl-auto: validate`（仅校验 public）；intel 单元单独 validate `intelligence`。
- 验收：空库启动演练（`AwnApiApplicationTests` 口径）在受限账号下迁移通过；`TradingRoleIsolationTests` / `IntelligenceIsolationTests` 全绿。
- 回滚：V29 无破坏性，账号引用改回 `awn_app` 即回现状。

### S4：部署切换与验收

- `deploy/docker-compose.yml`：api / worker 主源改 `AWN_DB_USER: awn_trading`，新增 intel 数据源连接 `awn_intel`；`.env.example` / `deploy/README.md` / `docs/deployment/structure.md` / `docs/deployment/production.md` 同步（含 `AWN_INTEL_PASSWORD` 与运行账号关系说明）。
- 生产收紧完成口径写入 `docs/architecture/intelligence-isolation.md`（当前该文件仍写「应用改连该角色属部署收紧步骤」）。
- 验收：compose 冒烟 + `TradingRoleIsolationTests` + `IntelligenceIsolationTests` + 一次空库演练；随后才能勾选 `todo.md` 审核整改 表一 P1。

## 风险与取舍

- **双 EntityManagerFactory**：额外内存与二级缓存单元；本仓单副本 + 中等表量，可接受。
- **跨单元调用无同事务**：当前情报无交易反向依赖，无此调用；未来若需情报写交易表，属违反隔离边界的架构变更，必须先改 ADR-0004。
- **迁移角色**：S3 不强制做（可直接用现有超管跑迁移），但保留 `awn_migration` 作为后续收敛项，避免永久依赖超管。
- **`awn_intel` 由容器初始化脚本创建**，非迁移轨对象；S1–S3 的测试夹具必须显式执行 `init-intel.sql`（现状 `IntelligenceIsolationTests` 已如此）。
- 测试矩阵扩大后本地与 CI 时长会上升，可在 `docs/testing/checklist.md` 记录实测增量。

## 待用户拍板

1. 是否按方案 A 推进（S1–S4）？
2. S3 是否本期内做迁移角色收敛（新增 V29），还是先接受「迁移仍由超管执行、运行账号受限」？
3. 是否允许在本仓引入第二数据源装配（`spring.datasource` 命名空间新增 intel 段）？

拍板前不动代码；本方案不改任何业务代码，仅作设计留档。
