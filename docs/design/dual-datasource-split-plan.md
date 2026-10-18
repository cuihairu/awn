# 运行时账号收紧：双数据源 / JPA 单元拆分方案（已代决按 A 推进，S1 / S2 / S4 已落地）

解决审核表一 P1「API / Worker 使用 PostgreSQL 引导超级用户」（`todo.md` 审核整改条 表一 P1）。
2026-10-19 巡检推进单授权代决：**按方案 A（S1–S4）推进**，S3 迁移角色收敛本期不做。
S1（JPA 单元拆分）、S4 代码前置与部署面（双数据源 + 独立迁移连接 + 受限账号切换）均已落地并冒烟验证（见下）。回滚口径见各阶段。

## 拆分前卡点现状（2026-10-19 复核时的证据；本表各行现状均已随 S1 / S4 修复）

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

### S1：情报 JPA 单元独立（无行为变化）【已落地 2026-10-19】

- 实际落地：
  - 新增 `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java`：显式声明交易 / 情报两个 `EntityManagerFactory`、两个 `JpaTransactionManager` 与两个 `@EnableJpaRepositories` 扫描器。必须全部显式声明的原因：Boot 4.1 的 `JpaBaseConfiguration` 对 `LocalContainerEntityManagerFactoryBean` / `TransactionManager` 均带 `@ConditionalOnMissingBean` 回退，而任一 `@EnableJpaRepositories` 存在又会整体关闭仓库自动装配（`DataJpaRepositoriesAutoConfiguration` 的 `@ConditionalOnMissingBean(JpaRepositoryConfigExtension)`）。
  - 交易 EMF 用 `setManagedClassNameFilter` 排除 `site.awn.intelligence.*` 实体——Spring 7 的 `@EntityScan` 只收集包名（`EntityScanPackages.getPackageNames()`），没有 excludeFilters 通道。
  - 交易仓库扫描器用 `@ComponentScan.Filter(REGEX)` 排除情报仓库，避免与情报扫描器重复注册。
  - `AwnWorkerApplication` 移除 `@EntityScan` / `@EnableJpaRepositories`（改由 PersistenceConfig 统一装配）。
  - `IntelligenceFacadeImpl` 全部 8 处 `@Transactional` 指定 `intelTransactionManager`。
- 验收：新增 `apps/awn-api/src/test/java/site/awn/persistence/PersistenceUnitSplitTests.java`（4 项断言：交易单元不含 intelligence 实体且仍映射交易实体；情报单元恰好映射 4 个 intelligence 实体；intel 事务管理器挂在情报单元；情报服务事务注解绑定 intel 事务管理器）+ 全量测试套件（api/worker 及全部上游模块）。
- 回滚：删 PersistenceConfig + 还原事务注解与 AwnWorkerApplication 注解，无数据变更。

### S2：受限账号接入集成测试

- 结构断言部分已随 S1 落地（`PersistenceUnitSplitTests`：单元映射边界 + 情报事务管理器绑定 + 双数据源绑定）。
- 账号路径部分以 2026-10-19 部署冒烟覆盖（见 S4 第 5 步：两应用进程真实以 `awn_trading` / `awn_intel` 连接起服务）；
  权限层负向断言由 `IntelligenceIsolationTests` / `TradingRoleIsolationTests` 常驻覆盖。
  全链路（下单→支付→回调→履约）自动化断言未单列——受限于测试容器需同时装配两套账号凭据，收益有限，暂不补。

### S3：迁移执行者与运行账号分离【已代决：本期不做】

- 已代决维持方案 A 原口径：**迁移轨不拆**，仍由高权限连接（`awn_app`）执行；V29 迁移角色收敛延后，避免改动已应用的迁移轨。
- S4 切换运行账号后，Flyway 必须继续走高权限连接（见 S4 第 2 条），因此本阶段不阻塞 S4。

### S4：部署切换与验收【已落地 2026-10-19】

1. 第二数据源接入【已落地】：`PersistenceConfig` 显式声明交易主源（`spring.datasource.*` + `spring.datasource.hikari.*`，Boot 配方复刻）与情报源 `spring.intel-datasource.*`（Hikari 平铺属性）；因定义第二个 `DataSource` bean 会触发 `DataSourceConfiguration.Hikari` 的 `@ConditionalOnMissingBean(DataSource)` 回退，主源必须显式。两应用 YAML 已加 intel 段与 `spring.flyway.url/user/password` 迁移段（均回退主源同值，默认行为不变）。验收：`PersistenceUnitSplitTests.unitsUseSeparateDataSources`（交易 EMF 绑主源、情报 EMF 绑情报源、两个独立连接池）+ 全量套件。
2. **迁移连接独立配置**【已落地】：`awn_trading` 对 `intelligence` 零权限且无 CREATE，无法执行 V8–V10 的 intelligence 建表；`spring.flyway.url/user/password` 支持独立迁移连接，部署指向 `awn_app`（compose `AWN_MIGRATE_DB_USER`）。
3. 账号密码来源【已落地】：
   - `awn_trading` 密码：`V29__trading_role_password.sql`（`ALTER ROLE ... PASSWORD '${trading_role_password}'`），占位符由各应用 `FlywayPlaceholderConfig`（`apps/awn-api/.../api/FlywayPlaceholderConfig.java`、`apps/awn-worker/.../worker/FlywayPlaceholderConfig.java`）的 `FlywayConfigurationCustomizer` 从 `AWN_TRADING_DB_PASSWORD` 读取（默认与 V12 建号值一致，幂等）——worker 生产虽关闭 Flyway，但 worker 测试会开启 `spring.flyway.enabled` 跑迁移，凡可执行迁移的进程都需提供该占位符。占位符不能写在 `spring.flyway.placeholders` 里——Spring 绑定 Map 值不解析嵌套占位符；同理 Flyway 对注释行内的占位符也做替换，V29 头注释只写变量名。
   - `awn_intel` 密码：`deploy/config/postgres/init-intel.sql` 参数化为 psql 变量 `:'intel_password'` + 无条件 `ALTER ROLE`（环境变量是唯一权威来源，幂等）；`deploy/config/postgres/init-intel.sh`（`docker-entrypoint-initdb.d/10-init-intel.sh`）从 `AWN_INTEL_DB_PASSWORD` 读入后以 `-v` 传参执行。SQL 挂在 `/opt/awn-init/` 而非 initdb.d，避免 entrypoint 以缺参状态自动执行。`IntelligenceIsolationTests` 执行前把 `:'intel_password'` 替换为测试常量（JDBC 不解析 psql 变量）。
4. `deploy/docker-compose.yml`【已落地】：api / worker 主源 `AWN_DB_USER: awn_trading` + `AWN_TRADING_DB_PASSWORD`；情报源 `AWN_INTEL_DB_USER/PASSWORD: awn_intel`；api 迁移连接 `AWN_MIGRATE_DB_USER: awn_app` + `AWN_MIGRATE_DB_PASSWORD`（worker 生产关闭 Flyway 不需要）；postgresql 服务加 `AWN_INTEL_DB_PASSWORD` 供 initdb 包装。`.env.example` / `deploy/README.md` / `docs/deployment/structure.md` / `docs/deployment/production.md` 已同步（旧 `AWN_INTEL_PASSWORD` 变量废除）。
5. 验收【已过 2026-10-19】：
   - 选择性测试：`IntelligenceIsolationTests`（参数化 init 脚本经替换后权限断言仍全过）、`TradingRoleIsolationTests`、`PersistenceUnitSplitTests`，共 7 项绿。
   - 等价部署冒烟（postgres:16-alpine 空卷容器 + 本仓 HEAD 打包的两应用 jar，env 与 compose 同构）：initdb wrapper 以 `AWN_INTEL_DB_PASSWORD` 建 `awn_intel`（`ALTER ROLE` 日志 + 受限账号直连成功）；Flyway 以 `awn_app` 一次通过 V1–V29（`V29` 把 `awn_trading` 密码对齐到自定义值 `smoke-trading-pw`）；api 以 `awn_trading`×20 + `awn_intel`×1、worker 以 `awn_trading`×10 + `awn_intel`×1 连接（`pg_stat_activity` 实证），liveness / readiness 均 UP。
   - `docker compose config` 渲染校验：api 环境为 `AWN_DB_USER: awn_trading` / `AWN_INTEL_DB_USER: awn_intel` / `AWN_MIGRATE_DB_USER: awn_app`，缺变量按 `:?` 拒绝。
   - 未执行项：`docker compose up -d --build` 全镜像构建（容器内全量 Maven 下载，时长/网络不可控）；冒烟以相同 jar + 相同 env 合同等价覆盖，镜像构建路径本次改动未触碰。

## 风险与取舍

- **双 EntityManagerFactory**：额外内存与二级缓存单元；本仓单副本 + 中等表量，可接受。
- **跨单元调用无同事务**：当前情报无交易反向依赖，无此调用；未来若需情报写交易表，属违反隔离边界的架构变更，必须先改 ADR-0004。
- **迁移角色**：S3 不强制做（可直接用现有超管跑迁移），但保留 `awn_migration` 作为后续收敛项，避免永久依赖超管。
- **`awn_intel` 由容器初始化脚本创建**，非迁移轨对象；S1–S3 的测试夹具必须显式执行 `init-intel.sql`（现状 `IntelligenceIsolationTests` 已如此）。
- 测试矩阵扩大后本地与 CI 时长会上升，可在 `docs/testing/checklist.md` 记录实测增量。

## 拍板记录（2026-10-19，依巡检推进单代决）

1. **按方案 A 推进（S1–S4）**：已代决采纳；S1 / S2 / S4 已落地，S3 依下条延后。
2. **S3 迁移角色收敛（`awn_migration`）**：本期不做，迁移仍由高权限连接（`awn_app`）执行（方案 A 原口径），后续可另起一期；`V29` 属 S4 密码来源步骤，不属 S3。
3. **第二数据源装配**：已代决允许并落地，命名空间 `spring.intel-datasource`。

推翻方式：S1 为纯装配变更，还原 `PersistenceConfig`、`IntelligenceFacadeImpl` 事务注解与 `AwnWorkerApplication` 注解即回原状；S4 改动均可通过 `.env` 账号回退。
