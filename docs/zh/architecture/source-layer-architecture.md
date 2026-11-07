# 源码分层架构

[English](../../architecture/source-layer-architecture.md) | 中文 · [术语表](source-layer-glossary.md) · [拆分理由](source-layer-split-rationale.md)

本图记录“先读源码”得到的职责层，而不是简单复述目录名。所有路径对应 `7e43d70`。

| 层 | 职责 | 代表实现 |
|---|---|---|
| L1 | 客户端渠道 | `apps/web/app/layout.tsx:7`; `apps/admin/src/App.tsx:17`; `apps/miniprogram/src/app.ts:17` |
| L2 | 共享前端包 | `packages/api-client/src/index.ts:9`; `packages/campaign-renderer/src/index.ts:11` |
| L3 | 组合根 | `apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`; `apps/awn-api/src/main/java/site/awn/api/AdminController.java:41-66` |
| L4 | 业务模块 | `pom.xml:26-46`; `modules/awn-order/src/main/java/site/awn/order/application/OrderWriteService.java:48-219` |
| L5 | 共享后端基础设施 | `modules/awn-shared/src/main/java/site/awn/shared/package-info.java:1-17`; `modules/awn-shared/src/main/java/site/awn/shared/outbox/OutboxDispatchService.java:17-114` |
| L6 | 运行进程 | `apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`; `apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:6-27` |
| L7 | 数据 | `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:38-96`; `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33` |
| L8 | 部署 | `deploy/docker-compose.yml:15-156`; `deploy/config/caddy/Caddyfile:4-20` |

## 调用面

1. 浏览器 web 引入 `@awn/api-client`、`@awn/campaign-renderer`、`@awn/ticket-components` 和 `@awn/ui`（`apps/web/package.json:10-15`）。其他渠道有自己的传输层：小程序经 Taro request 包装（`apps/miniprogram/src/lib/taro-transport.ts:10-37`），Flutter 公开只读接口经 Dio 包装（`apps/client/lib/src/api/awn_client.dart:15-55`）。
2. Caddy 把公共根路径路由到 `web:3000`，把 `/admin` 路由到 `admin:80`，把 `/api/v1` 和 `/actuator` 路由到 `awn-api:8080`（`deploy/config/caddy/Caddyfile:4-20`）。
3. API 控制器是薄组合适配器。例如订单创建只导入 `OrderFacade` 和命令/视图类型（`apps/awn-api/src/main/java/site/awn/api/OrderController.java:13-17,36-54`）；支付操作只导入订单门面和支付门面契约（`apps/awn-api/src/main/java/site/awn/api/PaymentController.java:13-19,44-98`）。
4. 后端内部模块 POM 依赖有向：catalog 依赖 event；inventory 依赖 catalog；marketplace 依赖 audit；order 依赖 inventory、marketplace、catalog；payment 依赖 order（`modules/awn-catalog/pom.xml`、`modules/awn-inventory/pom.xml`、`modules/awn-marketplace/pom.xml`、`modules/awn-order/pom.xml`、`modules/awn-payment/pom.xml`）。控制器包其余导入是 `site.awn.<module>.api`、显式暴露的领域异常或 `site.awn.shared` 工具。
5. Worker 调度不含业务规则。每个任务先检查开关，再调用门面，例如订单超时经 `OrderFacade`（`apps/awn-worker/src/main/java/site/awn/worker/task/OrderTimeoutTask.java:8-49`），发件箱分发经 `OutboxDispatchService`（`apps/awn-worker/src/main/java/site/awn/worker/task/OutboxDispatchTask.java:8-45`）。

## 状态存储

### PostgreSQL

- 主交易数据源使用 `spring.datasource`（`apps/awn-api/src/main/resources/application.yml:4-9`）。
- 情报数据源是独立 Hikari 单元，由 `spring.intel-datasource` 配置（`apps/awn-api/src/main/resources/application.yml:10-17`）。
- `PersistenceConfig` 显式注册交易仓库扫描器并排除情报仓库，再把情报仓库绑定到 `intelEntityManagerFactory` 和 `intelTransactionManager`（`modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:43-67`）。
- 顺序 SQL schema 位于 `modules/awn-shared/src/main/resources/db/migration/`，起点是 `V1__baseline.sql:1-20`；Flyway 只由 API 进程执行（`apps/awn-api/src/main/resources/application.yml:22-31`; `apps/awn-worker/src/main/resources/application.yml:22-24`）。
- 事务状态包括订单、库存、支付、目录、活动、专场、履约、市场、营销、通知、审计、图鉴、身份和共享消息表；JPA 代表有 `Order`（`modules/awn-order/src/main/java/site/awn/order/domain/Order.java:15-21`）和 `InventoryReservation`（`modules/awn-inventory/src/main/java/site/awn/inventory/domain/InventoryReservation.java:17-67`）。
- 情报状态包括原始观测、标准化观测、价格历史和数据源；代表仓库位于 `modules/awn-intelligence/src/main/java/site/awn/intelligence/infrastructure/`。

### Redis 与内存

- Redis 是可选限流存储。`RedisRateLimiter` 递增 `awn:ratelimit:<key>`，并在首次递增时设置过期时间（`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33`）。
- Redis 连接输入是 `spring.data.redis.host` 和 port（`apps/awn-api/src/main/resources/application.yml:32-35`）；生产用 `AWN_RATELIMIT_STORE` 选择共享存储（`apps/awn-api/src/main/resources/application-prod.yml:1-31`; `deploy/docker-compose.yml:70-73`）。
- store 为 `memory`或缺省时，`InMemoryRateLimiter` 是默认实现（`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/InMemoryRateLimiter.java:10-35`）。

### 进程内状态

- 请求级追踪状态保存在 `ThreadLocal`，过滤器 `finally` 清理（`apps/awn-api/src/main/java/site/awn/api/TraceIdFilter.java:14-55`）。
- 组合根没有权威客户端会话表；token 认证直接推导请求身份（`apps/awn-api/src/main/java/site/awn/api/auth/ApiAuthenticationFilter.java:15-77`）。

## 配置与生命周期

| 范围 | Schema/输入 | 运行效果 |
|---|---|---|
| API 传输 | `apps/awn-api/src/main/resources/application.yml:37-38` | HTTP 服务端口 `8080`。 |
| Worker 传输 | `apps/awn-worker/src/main/resources/application.yml:4-5,30-36` | 无业务 HTTP 服务；management 端口 `8081`。 |
| 数据库 | `apps/awn-api/src/main/resources/application.yml:4-29`; `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:68-96` | 交易连接池、情报连接池和迁移连接。 |
| Redis/限流 | `apps/awn-api/src/main/resources/application.yml:32-35,65-70`; `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/InMemoryRateLimiter.java:10-17` | 存储选择及运营接口资源组限额。 |
| 认证 | `apps/awn-api/src/main/resources/application.yml:59-64`; `apps/awn-api/src/main/java/site/awn/api/auth/ApiTokenService.java` | 运营 token 和 API token secret。 |
| Worker 任务 | `apps/awn-worker/src/main/resources/application.yml:43-61` | 全局开关、任务开关和固定周期。 |
| 镜像/容器 | `deploy/docker-compose.yml:15-156`; `deploy/docker/api.Dockerfile:5-41`; `deploy/docker/worker.Dockerfile:1-41` | 构建、非 root 执行、健康检查、依赖和卷。 |
| 反向代理 | `deploy/config/caddy/Caddyfile:4-20` | 公共路径路由。 |

API 启动不启用 Worker 调度（`apps/awn-api/src/main/resources/application.yml:55-58`）；Worker 启动设置 `awn.worker.enabled=true` 并关闭业务 web 服务（`apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:19-26`）。Compose 先启动 API，API 健康后才启动 Worker（`deploy/docker-compose.yml:77-106`）。API 与 Worker 镜像分离：API 暴露 `8080` 并定义健康检查（`deploy/docker/api.Dockerfile:38-40`），Worker 暴露 `8081`、无业务端口（`deploy/docker/worker.Dockerfile:1-41`）。

## 分层说明

### L1 客户端渠道

- **实现：** Next.js web 路由与布局（`apps/web/app/layout.tsx:7-42`）、React admin 视图（`apps/admin/src/App.tsx:17-62`）、Taro 小程序页面（`apps/miniprogram/src/app.ts:17-34`）、Flutter 公开客户端（`apps/client/lib/src/api/awn_client.dart:15-40`）。
- **配置 schema：** 框架与应用包定义（`apps/web/package.json:1-32`; `apps/admin/package.json:1-31`; `apps/miniprogram/package.json:1-43`; `apps/client/pubspec.yaml:1-38`）。
- **专属协议文件：** HTTP DTO 位于 `packages/api-client/src/index.ts`；Flutter 客户端维护自有模型（`apps/client/lib/src/models/`）。
- **文案挂载：** 各端页面标签与组件文案，如 `apps/web/app/layout.tsx:14-35`、`apps/admin/src/views/OrdersView.tsx:11-42`、`apps/miniprogram/src/pages/index/index.tsx:20-55`。

### L2 共享前端包

- **实现：** API 客户端与 DTO/错误语义（`packages/api-client/src/index.ts:9-167`）、UI 主题和组件（`packages/ui/src/themes.ts:14-61`）、票档契约（`packages/ticket-components/src/tier-card.tsx:11-91`）、专场渲染器（`packages/campaign-renderer/src/index.ts:1-56`; `packages/campaign-renderer/src/renderer.tsx:16-260`）。
- **配置 schema：** 包清单与 workspace 解析（`packages/*/package.json`; `pnpm-workspace.yaml:1-22`）。
- **专属协议文件：** `packages/campaign-renderer/src/index.ts` 定义 `schemaVersion 1` 和 13 类区块白名单（`packages/campaign-renderer/src/index.ts:10-53`）。
- **文案挂载：** 包内回退标签和组件字符串（`packages/ticket-components/src/tier-card.tsx:43-91`; `packages/campaign-renderer/src/renderer.tsx:96-240`）。

### L3 组合根

- **实现：** `apps/awn-api/src/main/java/site/awn/api/` 下 16 个控制器；三个 Servlet 过滤器（`apps/awn-api/src/main/java/site/awn/api/auth/ApiAuthenticationFilter.java:15-77`、`apps/awn-api/src/main/java/site/awn/api/AdminAuthFilter.java:29-96`、`apps/awn-api/src/main/java/site/awn/api/TraceIdFilter.java:14-55`）；共享异常映射器（`apps/awn-api/src/main/java/site/awn/api/ApiExceptionHandler.java:55-101`）。
- **配置 schema：** `apps/awn-api/src/main/resources/application.yml:4-70` 与 `apps/awn-api/src/main/resources/application-prod.yml`。
- **专属协议文件：** `apps/awn-api/src/main/java/site/awn/api/ApiExceptionHandler.java` 把模块异常映射到共享错误信封；`docs/api/contracts.md` 是对外契约文档。
- **文案挂载：** 控制器消息和过滤器错误文本（`apps/awn-api/src/main/java/site/awn/api/AdminAuthFilter.java:59-75`）。

### L4 业务模块

- **实现：** `pom.xml:26-46` 声明 14 个 Maven 模块。每个模块遵循 `api -> application -> domain -> infrastructure`；代表门面有 `modules/awn-order/src/main/java/site/awn/order/api/OrderFacade.java:7-42`、`modules/awn-inventory/src/main/java/site/awn/inventory/api/InventoryFacade.java:9-40`、`modules/awn-payment/src/main/java/site/awn/payment/api/PaymentFacade.java:11-40`。
- **配置 schema：** 模块 POM 与应用服务读取的 Spring 配置；Worker 开关在 `apps/awn-worker/src/main/resources/application.yml:43-61`。
- **专属协议文件：** 各模块 `api/package-info.java`、门面接口、命令/视图 record，以及选定 `domain/package-info.java` 异常导出（`modules/awn-order/src/main/java/site/awn/order/api/package-info.java:1-6`; `modules/awn-order/src/main/java/site/awn/order/domain/package-info.java:1-6`）。
- **文案挂载：** 领域状态/消息与门面 Javadoc。例如 `modules/awn-order/src/main/java/site/awn/order/package-info.java:1-15` 和 `modules/awn-star/src/main/java/site/awn/star/package-info.java:1-15` 描述模块职责。

### L5 共享后端基础设施

- **实现：** `awn-shared` 包含持久化、发件箱/收件箱、告警、脱敏、限流、追踪、URL 白名单和错误支撑（`modules/awn-shared/src/main/java/site/awn/shared/package-info.java:1-17`; `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:38-96`）。
- **配置 schema：** 数据源前缀（`spring.datasource`、`spring.intel-datasource`、`spring.flyway`，见 `apps/awn-api/src/main/resources/application.yml:4-31`）和 `awn.ratelimit.*`（`apps/awn-api/src/main/resources/application.yml:65-70`）。
- **专属协议文件：** `RateLimiter`、`OutboxEventHandler` 以及 `modules/awn-shared/src/main/java/site/awn/shared/` 下的模块面向 record/接口。
- **文案挂载：** `OutboxDispatchService` 中的运营日志/告警文本（`modules/awn-shared/src/main/java/site/awn/shared/outbox/OutboxDispatchService.java:106-131`）以及 `modules/awn-shared/src/main/java/site/awn/shared/alert/` 下的告警类别/级别。

### L6 运行进程

- **实现：** `apps/awn-api` 和 `apps/awn-worker`。API 主类只启动 Spring Boot（`apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`）；Worker 启用调度并关闭业务 web 服务（`apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:6-27`）。
- **配置 schema：** 两个 application YAML 加 `awn.worker.*`（`apps/awn-worker/src/main/resources/application.yml:43-61`）。
- **专属协议文件：** Worker 任务类就是调度协议，分别拥有开关和固定周期 key（`apps/awn-worker/src/main/java/site/awn/worker/task/OrderTimeoutTask.java:27-39`）。
- **文案挂载：** 任务 Javadoc 与日志消息（`apps/awn-worker/src/main/java/site/awn/worker/task/WaitlistPromotionTask.java:14-52`）。

### L7 数据

- **实现：** PostgreSQL 实体/仓库分成交易与情报两个持久化单元（`modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:43-67`）、Flyway SQL（`modules/awn-shared/src/main/resources/db/migration/V1__baseline.sql:1-20`）、Redis/内存限流实现（`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33`; `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/InMemoryRateLimiter.java:10-35`）。
- **配置 schema：** `spring.datasource`、`spring.intel-datasource`、`spring.flyway`、`spring.data.redis`、`awn.ratelimit`（`apps/awn-api/src/main/resources/application.yml:4-35,65-70`）。
- **专属协议文件：** 顺序 SQL 迁移集是持久化协议；`RateLimiter` 接口是外部状态协议（`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RateLimiter.java:5-17`）。
- **文案挂载：** 迁移注释与数据模型文档（`modules/awn-shared/src/main/resources/db/migration/V1__baseline.sql:1-20`; `docs/architecture/domain-model.md:1-80`）。

### L8 部署

- **实现：** Compose 服务包括 Caddy、web、admin、API、Worker、PostgreSQL、Redis 和 RustFS（`deploy/docker-compose.yml:15-156`），构建配方在 `deploy/docker/*.Dockerfile`。
- **配置 schema：** Compose 环境插值与 `.env` 必填要求（`deploy/docker-compose.yml:28-73,95-103,111-145`）。
- **专属协议文件：** `deploy/config/caddy/Caddyfile:4-20`；各 `deploy/docker/*.Dockerfile` 是进程构建协议。
- **文案挂载：** `deploy/README.md`、`docs/deployment/production.md`、`docs/deployment/structure.md`。

