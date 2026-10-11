# ADR-0007：数据访问层采用 JPA + 关键路径原生 SQL

- 状态：已接受
- 日期：2026-10-11

## 背景

AWN 交易核心对 SQL 有硬性要求（ADR-0002）：库存原子条件 UPDATE、Outbox 与订单同事务、行锁与版本校验。同时平台还有大量普通 CRUD 与关联查询（活动、票档、专场、营销、情报）。备选方案有两种极端：全量 JPA（热路径 SQL 被 ORM 生成，难以精确控制并发语义）或全量 MyBatis-Plus（SQL 全部显式，但实体映射与 DDL 治理成本上升）。

## 决策

采用 **Spring Data JPA + 关键路径原生 SQL**：

- JPA：负责领域实体、常规查询（方法名派生 / JPQL）、关联与级联关系。
- 原生 SQL（`@Query(nativeQuery = true)` 或 `JdbcTemplate`）：**库存扣减、资金类操作、Outbox 插入、行锁（`SELECT ... FOR UPDATE`）必须手写 SQL**，禁止交给 ORM 生成。
- Schema 一律由 Flyway 管理（`ddl-auto: validate`），不使用 JPA 自动建表。

## 后果

- 正面：常规 CRUD 开发成本最低；热路径 SQL 100% 可控、可 review、可用真实 PostgreSQL 跑集成测试。
- 负面：两种写法并存，需要靠模块约定与 review 保持一致性。
- 约束：新增库存、扣减、资金、Outbox 类 SQL 必须使用原生 SQL，并配套 Testcontainers 集成测试；禁止通过 JPA 的自动刷写路径修改库存行。

## 备选方案

- **MyBatis-Plus 全量显式 SQL**：SQL 完全可控、国内团队熟悉度更高，但实体映射与 DDL 治理需手工维护，且与 Spring Modulith 的领域模型风格冲突。团队如整体偏好，可另立 ADR 替换本决策（本阶段已评估后不切换）。
- **纯 JPA**：热路径并发语义不可控，违反 ADR-0002。
