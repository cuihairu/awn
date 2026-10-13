# AWN 实施盘点（todo.md）

> 建账：2026-10-13（重新盘点并逐条对照代码核实）。依据 `docs/implementation-plan.md`（Phase 0–09）、`docs/architecture/*.md`、`docs/adr/*.md`、README。
> 口径：**代码落地 + 测试通过 + commit 证据**才可勾选 `[x]`；设计文档/原型一律不得记为已完成。
> 三档：已实现 / 只有设计 / 完全没有。每条带验收口径与期次。

---

## 一、已实现（代码 + 测试 + commit 证据）

### Phase 0 · 架构基线
- [x] 架构与契约文档 8 篇 + ADR 0001–0008 — acaf107、70e0ae9；`pnpm -C docs build` 通过

### Phase 01 · 工程骨架与模块治理
- [x] Maven 多模块（14 业务模块 + awn-api/awn-worker，Spring Boot 3.5.6 + Modulith 1.4.2）— acaf107
- [x] API 与 Worker 双入口（任务开关 `awn.worker.tasks.*`，迁移只由 API 执行）— acaf107
- [x] Flyway 迁移骨架与约定（V1__baseline.sql；worker 禁用 flyway）— acaf107
- [x] Testcontainers + Modulith 结构测试（ModularityTests 通过）— acaf107
- [x] CI 门禁三 job（backend / frontend docs+web+admin / flutter analyze）— acaf107、7c5f50f
- [x] 文档站 GitHub Pages 部署 — 2eef133
- [x] 统一异常体系与错误码（`ErrorCode`/`ErrorResponse`/`ApiExceptionHandler`）— 8789d10；ApiExceptionHandlerTests 4 例
- [x] 结构化日志与请求追踪 + 字段脱敏（TraceId/Traces/Masking）— f71e80b；11 例
- [x] Phase 01 收尾验收（空库迁移演练 + 健康检查 + 真实 PG 测试）— 3f1b984；`AwnApiApplicationTests` 绿

### Phase 02 · 活动、商品与专场
- [x] 活动、场次、票档、商品数据模型与迁移（V4）— e957603
- [x] 统一商品 API（详情 + 可售摘要，下单重校验）— f08c3e8；ProductApiTests 5 例
- [x] 专场草稿、配置校验、发布版本（不可变 revision）— 6bf6b96
- [x] 模块白名单与受控资源引用（13 类 section）— 6bf6b96；CampaignRenderingTests 8 例
- [x] 至少两种专场模板渲染（motorsport / concert）— 6bf6b96
- [x] 商品发布 API（DRAFT→PUBLISHED 状态守卫，`publishedProductsBySession` 可查到）— 30bbd8f；CatalogModuleTests 新增 3 例；全量 106 例绿

### Phase 03 · 库存与订单核心
- [x] 库存池表 + 原子条件 UPDATE 预留/确认/释放 + 流水（V2）— acaf107；InventoryConcurrencyTests 4 例
- [x] 订单 + 明细快照 + 状态机（明细 DB 触发器不可变）— OrderCoreTests
- [x] 订单幂等键与数据库唯一约束 — OrderCoreTests（replay / conflict / 并发同键）
- [x] 订单创建同事务写预留记录与 Outbox — OrderCoreTests.insufficientInventoryRollsBackEverything
- [x] 订单超时释放与库存对账 Worker 任务 — 72de1a4；OrderTimeoutServiceTests 4 例 + WorkerTasksTests 5 例
- [x] Phase 03 验收：并发压测 + 一致性断言 — adc174e、60bf73d

### Phase 04 · 支付、退款与可靠异步处理
- [x] 支付渠道接口 + 测试适配器（沙箱渠道）— a54dca2
- [x] 支付回调验签、去重、状态更新（幂等）— a54dca2；PaymentModuleTests
- [x] 退款状态机 — a54dca2
- [x] Outbox 分发 Worker + Inbox 消费 — a54dca2
- [x] 支付/退款/库存对账任务 — a54dca2

### Phase 05 · 履约与卖家挂牌
- [x] 卖家资料、供货审核、挂牌（awn-marketplace）— 3671c1b
- [x] 独立票品状态与供货凭证 — 3671c1b
- [x] 交付流程与异常处理（履约失败 → 订单退款流程）— 3671c1b

### Phase 06 · 票务情报与运营完善
- [x] 情报来源适配器 + 原始观察 + 标准化（intelligence schema）— 7377898；IntelligenceModuleTests 5 例 + IntelligenceIsolationTests 1 例
- [x] 价格历史、数据质量检查与来源可信度展示 — 7377898
- [x] 审计、监控、告警 — 7377898；AdminApiTests 6 例
- [x] 备份恢复演练 + 压测报告 + 生产部署手册 — e577bba

### Phase 08 · 交付工程化
- [x] 默认构建：一条命令出可运行产物（`scripts/build.sh`）— 1971d0c；全绿 1m34s
- [x] Docker 镜像：多阶段构建（api/worker/web）— 1971d0c；admin 镜像卡网络，需正常网络环境构建
- [x] docker-compose 一键起 + 一键验证 — 1971d0c；4/4 healthy，`curl 127.0.0.1:18080/actuator/health` UP

### Phase 09 · 业务全链审查
- [x] 商品/票档/票品/库存池/卖家/供货/挂牌关系澄清 + 全链时序图 + 不变量→测试清单 — 5c25520
- [x] 情报隔离表述与账号权限核对 — 4378dff
- [x] `campaign_assets` 表收录进 domain-model.md 总表 — 5c25520
- [x] `inbox_messages` 去重职责补入 async-outbox.md — 44db34c
- [x] 库存对账覆盖 total 与 ADJUST 流水累计 — fc5b776；InventoryReconciliationTests 2 例；全仓 91+4 绿

---

## 二、只有设计（文档有设计/规划，代码未落地或仅空壳）

> 期次指 implementation-plan 阶段。验收口径引自对应架构文档。

### 模块空壳（Phase 01 规划职责）——已清空：三模块均已落地
- [x] identity 模块（注册：邮箱唯一 + BCrypt 口令哈希 + 默认 BUYER 角色；登录：凭据校验，未知邮箱/错误口令同一异常不泄露账号；角色：授予幂等、hasRole 未知用户返回 false）— dbbbc86；IdentityModuleTests 4 例；全量 127 例绿
- [x] promotion 模块（券模板创建校验、发放三重限额——有效期窗口 + 配额守卫递增 + 每人限领一张唯一约束、核销 UNUSED→USED 守卫迁移、收券列表；与订单金额联动属后续裁决，不动订单）— dc04b4d；PromotionModuleTests 6 例；全量 123 例绿
- [x] notification 模块（站内信通道落地：`NotificationFacade` 统一投递留痕，INBOX 通道真实落库、EMAIL/SMS 显式未接入占位；任何渠道失败记 FAILED 不抛——通知失败不回滚订单；`ORDER_CREATED`/`ORDER_PAID` 事件经 Outbox 消费者投递买家站内信）— 7d8acdf；NotificationModuleTests 4 例；全量 117 例绿

### 交易全链缺口（trading-model.md G1–G6，均有建议方向）
- [x] G1 createPool 经 catalog api 校验商品编码存在（未知编码抛 ProductNotFoundException 且不留残余池）— 08c0ae2；InventoryConcurrencyTests 新增 1 例共 5 例；全量 113 例绿
- [ ] G2 挂牌/票品未接入订单链路（第三方挂牌事实上不可交易）— 建议：订单行增加凭证型（`listingId`/`unitNo`），预留改票品级状态迁移；补下单→挂牌联动测试。期次：Phase 05 收尾前裁决
- [x] G4 供货审核额度消耗校验（platform_guaranteed 挂牌消耗审核 quantity，额度耗尽拒绝；卖家自担挂牌不消耗）— dd4b9d7；MarketplaceModuleTests 新增 1 例共 8 例；全量绿
- [x] G5 支付/退款受理渠道调用移出事务（先落 CREATED，受理成功后 markPending；渠道故障保留 CREATED 交对账闭环）— 1568962；PaymentModuleTests 新增 2 例共 10 例；全量 100 例绿
- [x] G6 PAYMENT_UNKNOWN 消费者落地（Worker 核实任务逐单查渠道，迁移 UNKNOWN → SUCCEEDED/FAILED，渠道未定保持待核实；查实成功但订单已终态 → 告警 + 原路全额退款，不假装履约）— 708b1b8；PaymentModuleTests 新增 3 例共 13 例；全量 104 例绿

### 异步与运维缺口
- [x] 库存预留到期扫描（带 TTL 预留过期自动释放 + 幂等）— 9768f78；InventoryExpiryTests 3 例；全量 94 例绿
- [x] 交易角色最小权限化（V12 建 `awn_trading`：仅 public 交易表组读写、无 CREATE、对 intelligence schema 零权限，默认授权亦撤销；验证见 TradingRoleIsolationTests；应用改连该角色仍属部署收紧步骤，见 production.md）— 9fbd92e；全量 119 例绿
- [x] 资金类操作 SQL 原生化（支付/退款状态守卫更新、退款渠道受理、支付退款状态联动、全部 Outbox 插入均改原生 SQL，ADR-0007 偏差已消除；退款回调新增守卫更新）— 0b122bc；PaymentModuleTests 14 例、OrderTimeoutServiceTests 4 例绿（全量 108 例仅并行会话在途的情报 2 例失败）
- [x] 订单 `FULFILLING`/`FULFILLED` 联动（履约单创建发 `FULFILLMENT_STARTED` → 订单 FULFILLING；平台核验通过发 `FULFILLMENT_COMPLETED` → 订单 FULFILLED；守卫迁移 + 事件重放幂等）— 8997e33；FulfillmentModuleTests 7 例、PaymentModuleTests 14 例绿（全量 109 例仅并行会话在途的情报 2 例失败）

### 前端与共享包（依赖 Phase 07 原型过目 / 后端端点）
- [ ] 四个共享包（ui / campaign-renderer / ticket-components / api-client）— 各仅 1 个 stub 文件，零消费方零测试；验收：至少一端接入 + 组件测试
- [ ] apps/web（Next.js）接真实 API 渲染统一商品列表 — 后端列表端点已落地：`GET /api/v1/sessions/{sessionId}/products`（仅已发布商品、按创建顺序、available 为库存展示口径、未知场次 `SESSION_NOT_FOUND` 404）；验收：专场页渲染真实商品 + 可售状态（前端待 Phase 07 原型过目后实现）
- [ ] apps/admin（Vite+React）最小活动/专场编辑 + 发布流 — 后端已落地（AdminApiTests 6 例，7377898）；前端三视图待 Phase 07 原型过目后实现
- [ ] apps/miniprogram（Taro）白名单 section 子集渲染 + 下单链路 — 待 Phase 03；验收：渲染契约子集 + 下单链路
- [ ] apps/client（Flutter）从契约生成模型并跑通只读链路 — 待 Phase 03；需先 `flutter create .`；验收：只读活动/商品列表

### 其他设计未落地
- [x] 情报采集调度器（`collectAll` 全量采集入口 + worker 任务按 `intelligence-collection-ms`（默认 1800s）周期执行，开关 `awn.worker.tasks.intelligence-collection`；停用来源跳过、手动单源采集不受限、单源失败记录在报告中不中断整轮）— 44c6094；IntelligenceModuleTests 新增 2 例共 7 例 + IntelligenceCollectionTaskTest 2 例；全量 119 例绿
- [x] Redis 应用代码（限流用例；缓存与短期状态仍未用）— AdminAuthFilter 鉴权通过后按资源组限流（默认 100 次/60s，超限 429 + RATE_LIMITED），存储按 awn.ratelimit.store 选择（memory 进程内默认 / redis 多实例共享，compose 默认 redis），限流组件故障 fail-open；Redis 实现经 RedisRateLimiterIntegrationTest（真实 redis:7-alpine 容器）验证；RATE_LIMITED 补入错误码契约表 — eeee7a3；新增 11 例（内存限流 4 + Redis 容器 3 + 过滤器 4），全量 131 例绿
- [ ] 运营后台前端三视图 — 待 Phase 07 原型（同 apps/admin 条目）
- [ ] ErrorResponse 字段级校验明细（`fieldErrors`）— 暂缓，待多端接入后裁决
- [ ] Phase 07 四端 UI 原型重做与多主题体系（web/admin/client/miniprogram）— 原型已产出（`docs/design/prototypes/` + `docs/design/ui-system.md`），待用户过目；素材红线：不生成明星肖像/照片/logo 代餐，敏感信息用占位数据
- [ ] 活动页模板 + 三套实例（F1 / 周杰伦 / 周传雄）— 原型已产出，待用户过目
- [ ] 购买页全流程（选场次 → 票档/座位 → 数量实名 → 下单 → 支付 → 出票）— 原型已产出，待用户过目
- [ ] 每场次地理位置（城市/地址/坐标 + 地图）— 原型已产出，待用户过目
- [ ] 2D 座位图（P1）— 2D 原型已产出，待用户过目

---

## 三、完全没有（无设计无实现）

- [ ] 平台座位型票品模型 — trading-model.md 明确「未实现，需独立建模，禁止复用 `ticket_units`」（其外键挂 `listings`，语义是「卖家的票」）；目前无模型设计、无代码。验收：独立座位票品模型 + 逐座状态机（IN_STOCK→RESERVED/SOLD/DELIVERED/INVALID）+ 集成测试
- [ ] 3D 座位图（P2）— 明确「待 2D 过目后制作」；无设计无实现。验收：2D 过目后给出 3D 方案

---

## 挂起项（待用户裁决，不计入缺口）

- 座位图数据来源调研 → 立项决策 — 调研已交（dde227b）；待用户拍板后才动实现
- 座位图第一阶段范围与三问验证 — 待用户指定 5 个目标场馆与人力
- Phase 07 全部 UI 条目的原型过目与拍板
