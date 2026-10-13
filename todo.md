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

### 模块空壳（Phase 01 规划职责，当前仅 `package-info.java`）
- [ ] identity 模块：用户、登录、角色、授权 — 设计见 module-boundaries.md；验收：注册/登录/角色校验 + 集成测试
- [ ] promotion 模块：优惠券、活动优惠、使用限制 — 设计见 module-boundaries.md；验收：券创建/使用/限额 + 集成测试
- [ ] notification 模块：站内信、邮件、短信适配 — 设计见 module-boundaries.md；async-outbox.md 明确「通知发送未实现」；验收：至少一种通道适配 + 失败不回滚订单

### 交易全链缺口（trading-model.md G1–G6，均有建议方向）
- [ ] G1 `createPool` 不校验 `productCode` 存在性（字符串松耦合，编码写错静默成功）— 建议：createPool 经 catalog api 校验 + 集成测试。期次：Phase 03 收尾
- [ ] G2 挂牌/票品未接入订单链路（第三方挂牌事实上不可交易）— 建议：订单行增加凭证型（`listingId`/`unitNo`），预留改票品级状态迁移；补下单→挂牌联动测试。期次：Phase 05 收尾前裁决
- [ ] G4 供货审核 `quantity` 无额度消耗校验（一次 APPROVED 可挂任意多张 platform_guaranteed 票）— 建议：挂牌时累计校验审核额度 + 测试。期次：Phase 05
- [ ] G5 支付/退款受理事务内调用渠道（长事务持锁，违反「外部调用不出长事务」）— 建议：渠道调用移出事务（先落 CREATED，受理后 markPending）+ 测试。期次：Phase 04
- [ ] G6 `PAYMENT_UNKNOWN` 只落状态与事件，无消费者、无异常单流程；订单侧无 UNKNOWN 拦截（UNKNOWN 支付订单到期即释放）— 建议：PAYMENT_UNKNOWN 消费者 + 异常单处理流程 + 集成测试。期次：Phase 04

### 异步与运维缺口
- [x] 库存预留到期扫描（带 TTL 预留过期自动释放 + 幂等）— 9768f78；InventoryExpiryTests 3 例；全量 94 例绿
- [ ] 交易角色最小权限化（应用当前以引导超级用户连接，仅情报 `awn_intel` 角色落地）— intelligence-isolation.md；验收：交易角色只读/写边界 + 迁移
- [ ] 资金类操作 SQL 原生化（支付/退款状态迁移仍 JPQL 守卫更新，Outbox 插入走 JPA save）— ADR-0007 偏差；验收：关键资金 SQL 改原生 + 集成测试
- [ ] 订单 `FULFILLING`/`FULFILLED` 联动（枚举允许，无代码主动驱动；支付成功只置 PAID）— payment-state-machine.md；验收：履约驱动订单状态迁移 + 测试

### 前端与共享包（依赖 Phase 07 原型过目 / 后端端点）
- [ ] 四个共享包（ui / campaign-renderer / ticket-components / api-client）— 各仅 1 个 stub 文件，零消费方零测试；验收：至少一端接入 + 组件测试
- [ ] apps/web（Next.js）接真实 API 渲染统一商品列表 — 待后端列表端点 + 前端；验收：专场页渲染真实商品 + 可售状态
- [ ] apps/admin（Vite+React）最小活动/专场编辑 + 发布流 — 后端已落地（AdminApiTests 6 例，7377898）；前端三视图待 Phase 07 原型过目后实现
- [ ] apps/miniprogram（Taro）白名单 section 子集渲染 + 下单链路 — 待 Phase 03；验收：渲染契约子集 + 下单链路
- [ ] apps/client（Flutter）从契约生成模型并跑通只读链路 — 待 Phase 03；需先 `flutter create .`；验收：只读活动/商品列表

### 其他设计未落地
- [ ] 情报采集调度器 — intelligence-isolation.md 设计「采集调度器→来源适配器→原始观察」，当前仅手动触发采集接口；验收：按配置周期采集 + 手动触发仍可
- [ ] Redis 应用代码（缓存、限流、短期辅助状态）— system-overview.md「已接入部署栈，应用代码尚未使用」；验收：至少一个真实用例
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
