# AWN 实施盘点（todo.md）

> 建账：2026-10-13，按 2026-10-14 行格式规则重写。依据 `docs/implementation-plan.md`（Phase 0 到 09）、`docs/architecture/*.md`、`docs/adr/*.md`、README。
> 口径：代码落地 + 测试通过 + commit 证据才可勾选；设计文档与原型一律不得记为已完成。
> 格式：一行一个需求；完成行只留一条证据（commit hash + 测试条数）；未完成行保留需求与验收口径。

---

## 一、已实现（代码 + 测试 + commit 证据）

### Phase 0 · 架构基线
- [x] 架构与契约文档 8 篇 — acaf107；docs build 通过
- [x] ADR 0001 到 0008 — 70e0ae9；docs build 通过

### Phase 01 · 工程骨架与模块治理
- [x] Maven 多模块（14 业务模块 + awn-api/awn-worker）— acaf107；ModularityTests 2 例
- [x] 构建升级 Spring Boot 4.1.1 / Modulith 2.1.1 + Maven wrapper 3.10.0 — 4a700aa；mvnw verify 全量绿
- [x] API 入口（Spring Modulith 应用装配）— acaf107；AwnApiApplicationTests 1 例
- [x] Worker 入口与任务开关（`awn.worker.tasks.*`，迁移只由 API 执行）— 72de1a4；WorkerTasksTests 5 例
- [x] Flyway 迁移骨架与约定（V1__baseline.sql，worker 禁用 flyway）— acaf107；AwnApiApplicationTests 1 例
- [x] Testcontainers + Modulith 结构测试 — acaf107；ModularityTests 2 例
- [x] CI 门禁三 job（backend / frontend / flutter analyze）— acaf107、7c5f50f；三 job 全绿
- [x] CI 前端 job 跑共享包测试 — 83c91e3；pnpm test 19 例
- [x] 文档站 GitHub Pages 部署 — 2eef133；docs build 通过
- [x] 统一异常体系与错误码（`ErrorCode` / `ErrorResponse` / `ApiExceptionHandler`）— 8789d10；ApiExceptionHandlerTests 4 例
- [x] 请求追踪 TraceId 透传（TraceIdFilter + MDC）— f71e80b；TraceIdFilterTests 4 例
- [x] 结构化 trace 记录落库 — f71e80b；TracesTests 3 例
- [x] 日志字段脱敏 — f71e80b；MaskingTests 5 例
- [x] Phase 01 收尾验收（空库迁移演练 + 健康检查 + 真实 PG 测试）— 3f1b984；AwnApiApplicationTests 1 例

### Phase 02 · 活动、商品与专场
- [x] 活动、场次、票档、商品数据模型与迁移（V4）— e957603；CatalogModuleTests 6 例
- [x] 统一商品 API（详情 + 可售摘要，下单重校验）— f08c3e8；ProductApiTests 8 例
- [x] 专场草稿、配置校验、发布版本（不可变 revision）— 6bf6b96；CampaignModuleTests 5 例
- [x] 模块白名单与受控资源引用（13 类 section）— 6bf6b96；CampaignRenderingTests 8 例
- [x] 两种专场模板渲染（motorsport / concert）— 6bf6b96；CampaignRenderingTests 8 例
- [x] 商品发布 API（DRAFT 到 PUBLISHED 状态守卫，`publishedProductsBySession` 可查到）— 30bbd8f；CatalogModuleTests 6 例

### Phase 03 · 库存与订单核心
- [x] 库存池表 + 原子条件 UPDATE 预留/确认/释放 + 流水（V2）— acaf107；InventoryConcurrencyTests 5 例
- [x] 订单 + 明细快照 + 状态机（明细 DB 触发器不可变）— df1f467；OrderCoreTests 9 例
- [x] 订单幂等键与数据库唯一约束 — df1f467；OrderCoreTests 9 例
- [x] 订单创建同事务写预留记录与 Outbox — df1f467；OrderCoreTests 9 例
- [x] 订单超时释放任务 — 72de1a4；OrderTimeoutServiceTests 4 例
- [x] 库存对账 Worker 任务 — 72de1a4；WorkerTasksTests 5 例
- [x] Phase 03 验收（并发压测 + 一致性断言）— adc174e、60bf73d；InventoryStressTests 1 例

### Phase 04 · 支付、退款与可靠异步
- [x] 支付渠道接口 + 沙箱测试适配器 — a54dca2；PaymentModuleTests 14 例
- [x] 支付回调验签、去重、状态更新（幂等）— a54dca2；PaymentModuleTests 14 例
- [x] 退款状态机 — a54dca2；PaymentModuleTests 14 例
- [x] Outbox 分发 Worker + Inbox 消费 — a54dca2；PaymentModuleTests 14 例
- [x] 支付/退款/库存对账任务 — a54dca2；PaymentModuleTests 14 例

### Phase 05 · 履约与卖家挂牌
- [x] 卖家资料、供货审核、挂牌 — 3671c1b；MarketplaceModuleTests 8 例
- [x] 独立票品状态与供货凭证 — 3671c1b；MarketplaceModuleTests 8 例
- [x] 交付流程与异常处理（履约失败转订单退款流程）— 3671c1b；FulfillmentModuleTests 7 例

### Phase 06 · 票务情报与运营完善
- [x] 情报来源适配器 + 原始观察 + 标准化（intelligence schema）— 7377898；IntelligenceModuleTests 7 例
- [x] 情报库边界（读写只走 awn_intel，不写交易表）— 7377898；IntelligenceIsolationTests 1 例
- [x] 价格历史、数据质量检查与来源可信度展示 — 7377898；IntelligenceModuleTests 7 例
- [x] 审计、监控、告警 — 7377898；AdminApiTests 7 例
- [x] 备份恢复演练 + 压测报告 + 生产部署手册 — e577bba；docs build 通过

### Phase 08 · 交付工程化
- [x] 默认构建一条命令出可运行产物（`scripts/build.sh`）— 1971d0c；build.sh 全绿
- [x] Docker 镜像多阶段构建（api/worker/web）— 1971d0c；admin 镜像需正常网络环境构建
- [x] docker-compose 一键起 + 一键验证 — 1971d0c；4/4 healthy

### Phase 09 · 业务全链审查
- [x] 商品/票档/票品/库存池/卖家/供货/挂牌关系澄清 + 全链时序图 + 不变量到测试清单 — 5c25520；docs build 通过
- [x] 情报隔离表述与账号权限核对 — 4378dff；docs build 通过
- [x] `campaign_assets` 表收录进 domain-model.md 总表 — 5c25520；docs build 通过
- [x] `inbox_messages` 去重职责补入 async-outbox.md — 8f95761；docs build 通过
- [x] 库存对账覆盖 total 与 ADJUST 流水累计 — fc5b776；InventoryReconciliationTests 2 例

---

## 二、只有设计（文档有设计/规划，代码未落地或仅空壳）

> 期次指 implementation-plan 阶段，验收口径引自对应架构文档。

### 模块空壳（Phase 01 规划职责，三模块均已落地）
- [x] identity 模块（注册邮箱唯一 + BCrypt 哈希 + 默认 BUYER 角色；登录凭据校验不泄露账号；角色授予幂等）— dbbbc86；IdentityModuleTests 4 例
- [x] promotion 模块（券模板校验、发放三重限额、核销 UNUSED 到 USED 守卫、收券列表）— dc04b4d；PromotionModuleTests 6 例
- [x] notification 模块（`NotificationFacade` 统一投递留痕，INBOX 真实落库，EMAIL/SMS 显式占位，渠道失败不回滚订单）— 7d8acdf；NotificationModuleTests 4 例

### 交易全链缺口（trading-model.md G1 到 G6）
- [x] G1 createPool 经 catalog api 校验商品编码存在，未知编码不留残余池 — 08c0ae2；InventoryConcurrencyTests 5 例
- [x] G2 挂牌/票品接入订单链路（凭证型订单行 1:1，双守卫迁移同事务，并发抢单只放行一单）— a418f39；VoucherOrderModuleTests 9 例
- [x] G4 供货审核额度消耗校验（platform_guaranteed 消耗审核额度，卖家自担不消耗）— dd4b9d7；MarketplaceModuleTests 8 例
- [x] G5 支付/退款渠道调用移出事务（先落 CREATED，受理后 markPending，渠道故障交对账）— 1568962；PaymentModuleTests 14 例
- [x] G6 PAYMENT_UNKNOWN 消费者落地（Worker 核实任务迁移 UNKNOWN，查实成功但订单终态则告警退款）— 708b1b8；PaymentModuleTests 14 例

### 公开 HTTP 端点缺口（contracts.md 已列）
- [x] 活动/场次/情报公开读端点（GET /events、/events/{eventId}、/sessions/{sessionId}、/intelligence/events/{eventId}/prices）— 7c81622；PublicEventApiTests 13 例
- [x] 运营活动发布端点（POST /admin/events/{eventCode}/publish，DRAFT 到 PUBLISHED 守卫）— 4203f86；AdminApiTests 7 例
- [x] 订单 HTTP 端点（POST /orders、GET /orders/{orderId}、POST /orders/{orderId}/cancel）— 7096392；OrderApiTests 7 例
- [x] 支付与退款 HTTP 端点（POST/GET /orders/{orderId}/payments、POST /orders/{orderId}/refunds）— 9e049e6；PaymentApiTests 10 例
- [x] 履约与报价 HTTP 端点（GET /orders/{orderId}/fulfillment、POST /checkout/quote）— ed8d4f3；CheckoutApiTests 6 例

### 异步与运维缺口
- [x] 库存预留到期扫描（带 TTL 过期自动释放 + 幂等）— 9768f78；InventoryExpiryTests 3 例
- [x] 交易角色最小权限化（V12 建 `awn_trading`，仅交易表读写、对 intelligence schema 零权限）— 9fbd92e；TradingRoleIsolationTests 1 例
- [x] 资金类操作 SQL 原生化（支付/退款守卫更新与渠道受理、Outbox 插入均改原生 SQL，ADR-0007 偏差消除）— 0b122bc；PaymentModuleTests 14 例
- [x] 订单 FULFILLING/FULFILLED 联动（履约事件驱动订单状态，守卫迁移 + 事件重放幂等）— 8997e33；FulfillmentModuleTests 7 例

### 前端与共享包（依赖 Phase 07 原型过目 / 后端端点）
- [ ] 四个共享包（ui / campaign-renderer / ticket-components / api-client）— 验收：组件测试（已有 19 例，83c91e3）+ 至少一端消费（已接入 @awn/ui 至 apps/web 与 apps/admin）；余三包消费面为专场页、票档卡、下单页，待 Phase 07 原型过目后实现
- [ ] apps/web（Next.js）接真实 API 渲染统一商品列表 — 后端已落地 `GET /api/v1/sessions/{sessionId}/products`（7a6bd76）；验收：专场页渲染真实商品与可售状态，前端待 Phase 07 原型过目后实现
- [ ] apps/admin（Vite+React）最小活动/专场编辑 + 发布流 — 后端已落地（AdminApiTests 7 例，7377898）；验收：三视图编辑发布流，待 Phase 07 原型过目后实现
- [ ] apps/miniprogram（Taro）白名单 section 子集渲染 + 下单链路 — 后端 Phase 02 到 04 已全部落地；验收：渲染契约子集 + 下单链路，待 Phase 07 原型过目后实现
- [ ] apps/client（Flutter）从契约生成模型并跑通只读链路 — 工程上需先 `flutter create .` 生成平台工程；验收：只读活动/商品列表，待 Phase 07 原型过目后实现

### 其他设计未落地
- [x] 情报采集调度器（`collectAll` 入口 + worker 按 `intelligence-collection-ms` 周期执行，停用来源跳过，单源失败不中断整轮）— 44c6094；IntelligenceCollectionTaskTest 2 例
- [x] Redis 限流（按资源组限流，超限 429 RATE_LIMITED，存储 memory/redis 可选，限流组件故障 fail-open）— eeee7a3；限流相关 11 例
- [ ] 运营后台前端三视图 — 待 Phase 07 原型（同 apps/admin 条目）
- [ ] ErrorResponse 字段级校验明细（`fieldErrors`）— 暂缓，待多端接入后裁决
- [ ] Phase 07 四端 UI 原型重做与多主题体系（web/admin/client/miniprogram）— 原型已产出（`docs/design/prototypes/` + `docs/design/ui-system.md`），待用户过目；素材红线：不生成明星肖像/照片/logo 代餐，敏感信息用占位数据
- [ ] 活动页模板 + 三套实例（F1 / 周杰伦 / 周传雄）— 原型已产出，待用户过目
- [ ] 购买页全流程（选场次 到 票档/座位 到 数量实名 到 下单 到 支付 到 出票）— 原型已产出，待用户过目
- [ ] 每场次地理位置（城市/地址/坐标 + 地图）— 原型已产出，待用户过目
- [ ] 2D 座位图（P1）— 原型已产出，待用户过目

---

## 三、完全没有（无设计无实现）

- [x] 平台座位型票品模型（独立 `platform_seats`，逐座状态机，守卫条件 UPDATE 并发抢座只放行一单）— c7ea49c；PlatformSeatModuleTests 9 例（含并发抢座）
- [ ] 3D 座位图（P2）— 无设计无实现；验收：2D 过目后给出 3D 方案

---

## 挂起项（待用户裁决，不计入缺口）

- 座位图数据来源调研到立项决策 — 调研已交（dde227b），待用户拍板后才动实现
- 座位图第一阶段范围与三问验证 — 待用户指定 5 个目标场馆与人力
- Phase 07 全部 UI 条目的原型过目与拍板

- [ ] 文档去 AI 腔（来源：用户 2026-10-14）
  - 现象：**文档 AI 味道太重**——典型症状：空洞的总起句、"让我们/首先/其次/综上所述"式过渡、堆砌形容词、反复强调"强大/完善/灵活"、每段都要先铺垫再讲、结尾还要总结一遍
  - 要求：**改成工程文档口吻**——直接讲事实与用法，删掉没有信息量的修饰与铺垫；命令/参数/行为用**可验证的表述**（给命令、给输出、给约束），不许用形容词代替事实
  - 覆盖范围：README 与 docs 下的文档，逐篇过；**中文版与英文版同步改**，不要只改一种
  - 验收：抽查若干段落，无上述症状；改动只涉文档，不碰代码

