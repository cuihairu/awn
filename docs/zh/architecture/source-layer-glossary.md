# 源码分层术语表

[English](../../architecture/source-layer-glossary.md) | 中文

本文定义源码分层分析使用的抽象概念。路径与行号对应已检视的 `7e43d70` 提交。

| 术语 | 英文 | 定义 | file:line |
|---|---|---|---|
| 源码层 | Source layer | 按源码职责归纳的边界：客户端、共享包、组合根、业务模块、共享基础设施、运行进程、数据、部署。 | `pom.xml:26-46`; `deploy/docker-compose.yml:15-104` |
| 客户端渠道 | Client channel | 面向用户的进程或构建产物，拥有页面状态与本地展示，只通过 HTTP 访问交易状态。 | `apps/web/app/layout.tsx:7`; `apps/admin/src/App.tsx:17`; `apps/miniprogram/src/app.ts:17` |
| 共享前端包 | Shared frontend package | 被多个渠道复用的 workspace 包，封装 API 类型、UI、票档组件或专场渲染。 | `packages/api-client/src/index.ts:9`; `packages/ui/src/themes.ts:14`; `packages/campaign-renderer/src/index.ts:11` |
| API 客户端 | API client | 固定默认 `/api/v1`、定义跨渠道 DTO，并把非 2xx 归一为 `ApiError` 的 TypeScript 客户端。 | `packages/api-client/src/index.ts:9,143-167` |
| 渠道传输适配器 | Channel transport | 渠道专属 HTTP 包装：浏览器 `fetch`、Taro request 或 Flutter Dio。 | `apps/miniprogram/src/lib/taro-transport.ts:10-37`; `apps/client/lib/src/api/awn_client.dart:15-55` |
| 本地文案 | Local copy | 各端页面或组件内维护的标签与说明文案，不集中存放在后端。 | `apps/web/app/layout.tsx:14-35`; `apps/admin/src/views/OrdersView.tsx:11-42`; `apps/miniprogram/src/pages/index/index.tsx:20-55` |
| 设计令牌 | Design token | 由 `packages/ui` 拥有的跨端主题常量；`apps/web/app/globals.css` 只桥接结构样式。 | `packages/ui/src/themes.ts:14-61`; `apps/web/app/globals.css:1-12` |
| 组合根 | Composition root | Spring Boot API 应用，拥有 HTTP 路由、请求响应形态、认证、限流、追踪与领域异常到 HTTP 错误的映射。 | `apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`; `apps/awn-api/src/main/java/site/awn/api/ApiExceptionHandler.java:55-101` |
| HTTP 端点 | HTTP endpoint | 暴露在 `/api/v1` 下的控制器方法；组合根委托模块门面，不实现业务规则。 | `apps/awn-api/src/main/java/site/awn/api/OrderController.java:27-54`; `apps/awn-api/src/main/java/site/awn/api/PaymentController.java:33-98` |
| 认证过滤器 | Authentication filter | 控制器执行前解析 API token 或运营 token 的 Servlet 过滤器。 | `apps/awn-api/src/main/java/site/awn/api/auth/ApiAuthenticationFilter.java:15-77`; `apps/awn-api/src/main/java/site/awn/api/AdminAuthFilter.java:29-96` |
| 固定窗口限流 | Fixed-window rate limit | 同一 key 在窗口内最多放行 N 次的契约；默认内存实现，可切换 Redis。 | `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RateLimiter.java:5-17`; `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33` |
| 追踪标识 | Trace ID | 一次 HTTP 请求内透传或校验的 `X-Request-Id`；Worker 每轮任务生成独立 ID。 | `apps/awn-api/src/main/java/site/awn/api/TraceIdFilter.java:14-55`; `modules/awn-shared/src/main/java/site/awn/shared/trace/Traces.java:9-44` |
| 错误信封 | API error envelope | 由模块异常映射出的稳定 `code` / `message` 响应结构。 | `modules/awn-shared/src/main/java/site/awn/shared/error/ErrorResponse.java:8-39`; `apps/awn-api/src/main/java/site/awn/api/ApiExceptionHandler.java:55-101` |
| 运行进程 | Runtime process | 生命周期不同的可部署进程：HTTP API 或定时 Worker。 | `apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`; `apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:6-27` |
| 业务模块 | Business module | 含 `api`、`application`、`domain`、`infrastructure` 的 Maven 模块，拥有自己的用例和数据表。 | `modules/awn-order/src/main/java/site/awn/order/package-info.java:1-15`; `modules/awn-inventory/src/main/java/site/awn/inventory/package-info.java:1-15` |
| 模块边界 | Module boundary | Spring Modulith 包规则：其他模块只能使用命名公共接口和显式暴露的领域异常。 | `modules/awn-order/src/main/java/site/awn/order/api/package-info.java:1-6`; `modules/awn-order/src/main/java/site/awn/order/domain/package-info.java:1-6` |
| 公共接口 | Public interface | `api` 包中的门面契约，控制器与 Worker 任务都经它调用模块。 | `modules/awn-order/src/main/java/site/awn/order/api/OrderFacade.java:7-42`; `apps/awn-worker/src/main/java/site/awn/worker/task/OrderTimeoutTask.java:8-41` |
| 领域异常 | Domain exception | 模块显式暴露的错误契约，供组合根映射 HTTP 语义。 | `modules/awn-order/src/main/java/site/awn/order/domain/package-info.java:1-6`; `apps/awn-api/src/main/java/site/awn/api/ApiExceptionHandler.java:16-53` |
| 应用服务 | Application service | 协调领域状态、持久化、副作用与事务的模块用例层。 | `modules/awn-order/src/main/java/site/awn/order/application/OrderWriteService.java:48-219`; `modules/awn-payment/src/main/java/site/awn/payment/application/PaymentFacadeImpl.java:51-148` |
| 领域模型 | Domain model | 模块内部实体、值对象、状态机与不变量，比应用服务更稳定。 | `modules/awn-order/src/main/java/site/awn/order/domain/Order.java:14-132`; `modules/awn-payment/src/main/java/site/awn/payment/domain/Payment.java:14-132` |
| 事务边界 | Transaction boundary | 应用服务中的事务方法，把业务状态和消息记录一起提交。 | `modules/awn-order/src/main/java/site/awn/order/application/OrderWriteService.java:88-198`; `modules/awn-payment/src/main/java/site/awn/payment/application/PaymentFacadeImpl.java:88-130` |
| 持久化适配器 | Persistence adapter | `infrastructure` 包中的 JPA 实体和仓库，由应用服务使用。 | `modules/awn-order/src/main/java/site/awn/order/infrastructure/OrderRepository.java:13-44`; `modules/awn-inventory/src/main/java/site/awn/inventory/infrastructure/InventoryReservationRepository.java:15-57` |
| 交易持久化单元 | Trading persistence unit | 主 PostgreSQL 持久化单元，扫描非情报仓库并拥有交易状态。 | `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:43-57`; `apps/awn-api/src/main/resources/application.yml:4-9` |
| 情报持久化单元 | Intelligence persistence unit | 独立 PostgreSQL 持久化单元，只绑定 `site.awn.intelligence` 和情报 schema。 | `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:47-67,84-96` |
| PostgreSQL 交易数据 | PostgreSQL trading data | 身份、活动、目录、专场、库存、订单、支付、履约、市场、营销、通知、审计、共享消息和图鉴内容表。 | `modules/awn-shared/src/main/resources/db/migration/V1__baseline.sql:8-20`; `modules/awn-order/src/main/java/site/awn/order/domain/Order.java:15-21` |
| PostgreSQL 情报数据 | PostgreSQL intelligence data | 情报单元中的原始观测、标准化观测、价格历史和数据源表。 | `modules/awn-intelligence/src/main/java/site/awn/intelligence/infrastructure/RawObservationRepository.java:8-44`; `modules/awn-intelligence/src/main/java/site/awn/intelligence/domain/RawObservation.java:15-74` |
| Redis 限流存储 | Redis rate-limit store | 可选的多实例固定窗口计数存储，key 前缀为 `awn:ratelimit:`。 | `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33`; `apps/awn-api/src/main/resources/application.yml:32-35,65-70` |
| 内存限流存储 | In-memory rate-limit store | 无 Redis 或 `awn.ratelimit.store=memory` 时使用的默认单进程实现。 | `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/InMemoryRateLimiter.java:10-35` |
| 共享基础设施 | Shared infrastructure | `awn-shared` 中的消息、告警、脱敏、URL 校验、限流、追踪、错误和持久化支撑。 | `modules/awn-shared/src/main/java/site/awn/shared/package-info.java:1-17`; `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:38-96` |
| 事务性发件箱 | Transactional outbox | 与业务状态同事务写入的 `PENDING` 事件行，之后按消费者租约投递。 | `modules/awn-shared/src/main/java/site/awn/shared/outbox/OutboxMessage.java:14-86`; `modules/awn-shared/src/main/java/site/awn/shared/outbox/OutboxDispatchService.java:17-114` |
| 收件箱去重 | Inbox deduplication | 与消费者副作用同事务提交的 `(consumer, message_id)` 记录，使重放安全。 | `modules/awn-shared/src/main/java/site/awn/shared/inbox/InboxMessage.java:14-55`; `modules/awn-shared/src/main/java/site/awn/shared/outbox/OutboxDispatchService.java:84-100` |
| 定时任务 | Scheduled task | Worker 拥有的 Spring 调度，按固定延迟调用模块门面。 | `apps/awn-worker/src/main/java/site/awn/worker/task/OrderTimeoutTask.java:15-49`; `apps/awn-worker/src/main/java/site/awn/worker/task/IntelligenceCollectionTask.java:12-47` |
| 任务开关 | Task toggle | `awn.worker.enabled` 与 `awn.worker.tasks.*` 的两级开关，在每个任务内部检查。 | `apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:19-26`; `apps/awn-worker/src/main/java/site/awn/worker/task/OrderTimeoutTask.java:27-39` |
| 告警记录 | Alert record | 模块服务和发件箱分发器用来保留运营失败的共享持久化行。 | `modules/awn-shared/src/main/java/site/awn/shared/alert/AlertRecord.java:12-78`; `modules/awn-shared/src/main/java/site/awn/shared/alert/AlertRecorder.java:8-37` |
| 敏感信息脱敏 | Sensitive-data masking | 在持久化或日志前隐藏身份、凭证类数据的共享规整逻辑。 | `modules/awn-shared/src/main/java/site/awn/shared/mask/Masking.java:9-70` |
| 存储地址白名单 | Storage URL whitelist | 限制专场和媒体 URL 只能使用授权存储形态的共享校验。 | `modules/awn-shared/src/main/java/site/awn/shared/url/StorageUrlValidator.java:9-61`; `modules/awn-shared/src/main/java/site/awn/shared/url/package-info.java:1-9` |
| 专场配置协议 | Campaign schema protocol | 前端包实现的版本化专场区块契约和渲染白名单。 | `packages/campaign-renderer/src/index.ts:1-56`; `packages/campaign-renderer/src/renderer.tsx:16-260` |
| 专场区块白名单 | Campaign section whitelist | `schemaVersion 1` 允许的 13 类区块；未知类型校验失败且不渲染。 | `packages/campaign-renderer/src/index.ts:10-53`; `packages/campaign-renderer/src/renderer.tsx:223-259` |
| 数据库迁移 | Database migration | `awn-shared` 拥有的顺序 Flyway SQL，只由 API 进程执行。 | `apps/awn-api/src/main/resources/application.yml:22-31`; `apps/awn-worker/src/main/resources/application.yml:22-24` |
| 部署服务 | Deployment service | Compose 定义的进程或后端存储，包含镜像、环境、健康检查、依赖和卷生命周期。 | `deploy/docker-compose.yml:15-104,108-148`; `deploy/docker/api.Dockerfile:5-41` |
| 反向代理路由 | Reverse-proxy route | Caddy 把根路径、`/admin`、`/api/v1` 分别路由到 web、admin 和 API 的规则。 | `deploy/config/caddy/Caddyfile:4-20` |
| 运行账号 | Runtime role | 分别用于交易、情报和迁移的 PostgreSQL 最小权限身份。 | `apps/awn-api/src/main/resources/application.yml:4-29`; `deploy/docker-compose.yml:52-67,96-103` |
| 配置输入 | Configuration input | 提供数据库、Redis、密钥、任务开关、周期、限额和部署凭证的环境变量与 profile YAML。 | `apps/awn-api/src/main/resources/application.yml:4-70`; `deploy/docker-compose.yml:28-73,95-103,111-145` |
| 健康探针 | Health probe | Compose 和生命周期检查使用的 API HTTP health 端点或 Worker management 端点。 | `apps/awn-api/src/main/resources/application.yml:37-53`; `apps/awn-worker/src/main/resources/application.yml:30-36`; `deploy/docker-compose.yml:82-106` |
| 超大文件 | Oversized file | 超过 1000 行的源码或内容文件；即使无执行逻辑，也作为拆分候选记录。 | `apps/web/app/globals.css:1-2`; `apps/web/app/globals.css:1080` |

