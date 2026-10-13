# AWN 实施盘点（todo.md）

> 建账：2026-10-12。依据 `docs/implementation-plan.md`（Phase 0–06）、`docs/architecture/*.md`、`docs/adr/*.md`。
> 口径：代码落地 + 测试通过 + commit 证据才可勾选；完成项保留一行证据（hash + 测试数）。

## Phase 0 · 架构基线

- [x] 架构与契约文档 8 篇 + ADR 0001–0008 — acaf107、70e0ae9；`pnpm -C docs build` 通过

## Phase 01 · 工程骨架与模块治理

- [x] Maven 多模块（14 业务模块 + awn-api/awn-worker，Spring Boot 3.5.6 + Modulith 1.4.2）— acaf107
- [x] API 与 Worker 双入口（任务开关 `awn.worker.tasks.*`，迁移只由 API 执行）— acaf107
- [x] Flyway 迁移骨架与约定（V1__baseline.sql；worker 禁用 flyway）— acaf107
- [x] Testcontainers + Modulith 结构测试（ModularityTests 通过）— acaf107
- [x] CI 门禁三 job（backend / frontend docs+web+admin / flutter analyze）— acaf107、7c5f50f
- [x] 文档站 GitHub Pages 部署 — 2eef133
- [x] 统一异常体系与错误码（`ErrorCode`/`ErrorResponse`/`ApiExceptionHandler`）— 8789d10；ApiExceptionHandlerTests 4 例
- [x] 结构化日志与请求追踪 + 字段脱敏（TraceId/Traces/Masking）— f71e80b；11 例
- [x] Phase 01 收尾验收（空库迁移演练 + 健康检查 + 真实 PG 测试）— 3f1b984；`AwnApiApplicationTests` 绿

## Phase 02 · 活动、商品与专场

- [x] 活动、场次、票档、商品数据模型与迁移（V4）— e957603
- [x] 统一商品 API（详情 + 可售摘要，下单重校验）— f08c3e8；ProductApiTests 5 例
- [x] 专场草稿、配置校验、发布版本（不可变 revision）— 6bf6b96
- [x] 模块白名单与受控资源引用（13 类 section）— 6bf6b96；CampaignRenderingTests 8 例
- [x] 至少两种专场模板渲染（motorsport / concert）— 6bf6b96

## Phase 03 · 库存与订单核心

- [x] 库存池表 + 原子条件 UPDATE 预留/确认/释放 + 流水（V2）— acaf107；InventoryConcurrencyTests 4 例
- [x] 订单 + 明细快照 + 状态机（明细 DB 触发器不可变）— OrderCoreTests
- [x] 订单幂等键与数据库唯一约束 — OrderCoreTests（replay / conflict / 并发同键）
- [x] 订单创建同事务写预留记录与 Outbox — OrderCoreTests.insufficientInventoryRollsBackEverything
- [x] 订单超时释放与库存对账 Worker 任务 — 72de1a4；OrderTimeoutServiceTests 4 例 + WorkerTasksTests 5 例
- [x] Phase 03 验收：并发压测 + 一致性断言 — adc174e、60bf73d

## Phase 04 · 支付、退款与可靠异步处理

- [x] 支付渠道接口 + 测试适配器（沙箱渠道）— a54dca2
- [x] 支付回调验签、去重、状态更新（幂等）— a54dca2；PaymentModuleTests
- [x] 退款状态机 — a54dca2
- [x] Outbox 分发 Worker + Inbox 消费 — a54dca2
- [x] 支付/退款/库存对账任务 — a54dca2

## Phase 05 · 履约与卖家挂牌

- [x] 卖家资料、供货审核、挂牌（awn-marketplace）— 3671c1b
- [x] 独立票品状态与供货凭证 — 3671c1b
- [x] 交付流程与异常处理（履约失败 → 订单退款流程）— 3671c1b

## Phase 06 · 票务情报与运营完善

- [x] 情报来源适配器 + 原始观察 + 标准化（intelligence schema）— 7377898；IntelligenceModuleTests 5 例 + IntelligenceIsolationTests 1 例
- [x] 价格历史、数据质量检查与来源可信度展示 — 7377898
- [ ] 运营后台（apps/admin）核心页面接真实 API — 后端已落地（AdminApiTests 6 例，7377898）；前端三视图待 Phase 07 原型过目后实现
- [x] 审计、监控、告警 — 7377898；AdminApiTests 6 例
- [x] 备份恢复演练 + 压测报告 + 生产部署手册 — e577bba

## Phase 07 · 四端界面重做（挂起：待用户过目原型）

> 原型已产出（`docs/design/prototypes/` + `docs/design/ui-system.md`），全部条目挂起等待用户过目与拍板。素材红线：不生成明星肖像/照片/logo 代餐，敏感信息用占位数据。

- [ ] 四端 UI 原型重做与多主题体系（web/admin/client/miniprogram）— 原型已产出，待用户过目
- [ ] 活动页模板 + 三套实例（F1 / 周杰伦 / 周传雄）— 原型已产出，待用户过目
- [ ] 购买页全流程（选场次 → 票档/座位 → 数量实名 → 下单 → 支付 → 出票）— 原型已产出，待用户过目
- [ ] 每场次地理位置（城市/地址/坐标 + 地图）— 原型已产出，待用户过目
- [ ] 2D/3D 可切换座位图（P1 2D / P2 3D）— 2D 原型已产出待过目；3D 待 2D 过目后制作
- [ ] 座位图数据来源调研 → 立项决策 — 调研已交（dde227b）；待用户拍板后才动实现
- [ ] 座位图第一阶段范围与三问验证 — 待用户指定 5 个目标场馆与人力

## Phase 08 · 交付工程化

- [x] 默认构建：一条命令出可运行产物（`scripts/build.sh`）— 1971d0c；全绿 1m34s
- [x] Docker 镜像：多阶段构建（api/worker/web）— 1971d0c；admin 镜像卡网络，需正常网络环境构建
- [x] docker-compose 一键起 + 一键验证 — 1971d0c；4/4 healthy，`curl 127.0.0.1:18080/actuator/health` UP

## Phase 09 · 业务全链审查

- [x] 商品/票档/票品/库存池/卖家/供货/挂牌关系澄清 + 全链时序图 + 不变量→测试清单 — 5c25520
- [x] 情报隔离表述与账号权限核对 — 4378dff
- [x] `campaign_assets` 表收录进 domain-model.md 总表 — 5c25520
- [x] `inbox_messages` 去重职责补入 async-outbox.md — 44db34c
- [x] 库存对账覆盖 total 与 ADJUST 流水累计 — fc5b776；InventoryReconciliationTests 2 例；全仓 91+4 绿

## 跨阶段缺口

- [ ] 四个共享包零消费方、零测试 — 待前端接入
- [ ] apps/web（Next.js）接真实 API 渲染统一商品列表 — 待后端列表端点 + 前端
- [ ] apps/admin（Vite+React）最小活动/专场编辑 + 发布流 — 待 Phase 07 原型过目
- [ ] apps/miniprogram（Taro）白名单 section 子集渲染 + 下单链路 — 待 Phase 03
- [ ] apps/client（Flutter）从契约生成模型并跑通只读链路 — 待 Phase 03；需先 `flutter create .`

## 错误信封可选增强

- [ ] ErrorResponse 字段级校验明细（`fieldErrors`）— 暂缓，待多端接入后裁决

## 挂起项说明

- Phase 07 全部条目：待用户过目原型 / 拍板（座位图立项、5 场馆试点名单）。
- 运营后台前端：依赖 Phase 07 原型。
- 前端四端与共享包：依赖后端 HTTP 端点与契约落地。
