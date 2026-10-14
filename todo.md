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
- [ ] 四个共享包（ui / campaign-renderer / ticket-components / api-client）— 验收：组件测试（已有 19 例，83c91e3）+ 至少一端消费；已消费：ui（web + admin）、api-client（web 全部页面）、ticket-components（TierCard 票档卡，f348026）；余 campaign-renderer 专场页消费面，待专场页实现
- [x] apps/web（Next.js）接真实 API 渲染统一商品列表 — 验收：专场页渲染真实商品与可售状态 — f348026；前端 78 例 + PublicEventApiTests 16 例（含新增场次列表端点 3 例）
  - 已代决（2026-10-14 巡检授权）：① 场次可见性 = 父活动可见性（`Event::isPublic`），不单独设场次发布流（SessionStatus 留潜态）；② 可售状态由 available 派生：null→未开售 / 0→已售罄 / ≤20→余票紧张，不发明后端没有的状态；③ 后端购买规则未落地前 TierCard 不渲染限购行；④ 新增 `GET /api/v1/events/{eventId}/sessions` 公开场次列表（父活动未公开→404，按开始时间升序）供活动页选场次
  - 交付面：/ 发现列表、/events/{id} 活动页（Hero + 场次列表）、/sessions/{id} 票档页（真实商品 + TierCard 可售状态）；封面用几何占位（素材红线）
- [x] apps/admin（Vite+React）最小活动/专场编辑 + 发布流 — 验收：三视图编辑发布流 — 3cf8dda；前端 90 例 + AdminApiTests 7 例（含全状态活动列表断言）
  - 交付面：订单视图（GET /admin/orders + 状态筛选）、库存中心（池余额 + 可用量占比预警条 + 调整表单 POST /inventory/adjustments + 审计记录）、活动发布流（GET /admin/events 全状态列表，草稿可发布，重复发布 409 提示）、异常视图（未确认告警 + 确认）；令牌 X-Admin-Token 输入并 localStorage 记忆
  - 已代决（2026-10-14 巡检授权）：① 新增 `GET /api/v1/admin/events`（EventFacade.allEvents，全状态按 id 升序）作为发布流入口；② 原型的「场次配置」视图暂缓——地理/座位分区后端字段未落地（todo 125），先接的后端就接（订单/库存/发布/异常四视图），不为无后端的表单造假；表单展示列（商品名/场次名）后端未下发的不渲染
- [ ] apps/miniprogram（Taro）白名单 section 子集渲染 + 下单链路 — 后端 Phase 02 到 04 已全部落地；验收：渲染契约子集 + 下单链路，原型过目已代决（2026-10-14 巡检授权）
- [ ] apps/client（Flutter）从契约生成模型并跑通只读链路 — 工程上需先 `flutter create .` 生成平台工程；验收：只读活动/商品列表，原型过目已代决（2026-10-14 巡检授权）

### 其他设计未落地
- [x] 情报采集调度器（`collectAll` 入口 + worker 按 `intelligence-collection-ms` 周期执行，停用来源跳过，单源失败不中断整轮）— 44c6094；IntelligenceCollectionTaskTest 2 例
- [x] Redis 限流（按资源组限流，超限 429 RATE_LIMITED，存储 memory/redis 可选，限流组件故障 fail-open）— eeee7a3；限流相关 11 例
- [x] 运营后台前端三视图 — 同 apps/admin 条目 — 3cf8dda；订单 / 库存（调整 + 审计）/ 活动发布流（+异常视图），场次配置视图随 todo 125 地理后端落地后接入
- [ ] ErrorResponse 字段级校验明细（`fieldErrors`）— 已代决（2026-10-14 巡检授权）：维持暂缓。理由：现行 `ErrorResponse(code, message, traceId)` 已可定位错误；四端原型均用内联 `.hint` 提示，无字段级错误展示诉求；待真正出现多端复用字段校验报错的需求再引入
- [ ] Phase 07 四端 UI 原型重做与多主题体系（web/admin/client/miniprogram）— 已代决（2026-10-14 巡检授权）原型过目：四端原型 + ui-system.md 走查完成，对照 13 项待办验收口径全覆盖，无阻塞缺陷；余多主题切换的组件化落地随各端实现推进；素材红线：不生成明星肖像/照片/logo 代餐，敏感信息用占位数据
- [ ] 活动页模板 + 三套实例（F1 / 周杰伦 / 周传雄）— 原型过目已代决（2026-10-14 巡检授权）；模板区块①Hero + ③场次列表已接真实 API（f348026），区块②演出信息 / ④票档 / ⑤须知等后台可配置内容与三套实例待专场内容接入
- [ ] 购买页全流程（选场次 到 票档/座位 到 数量实名 到 下单 到 支付 到 出票）— 原型过目已代决（2026-10-14 巡检授权），覆盖 web/miniprogram/client 三端购买流与座位图场景，无阻塞缺陷；实现待续（web 端票档页已就绪 /sessions/{id}，下单链路后续接入）
- [ ] 每场次地理位置（城市/地址/坐标 + 地图）— 原型过目已代决（2026-10-14 巡检授权）；实现待续：需后端场馆/地理字段迁移 + 运营配置 + 前端 geo-row 展示（样式已备，f348026）
- [ ] 2D 座位图（P1）— 原型过目已代决（2026-10-14 巡检授权，seatmap-2d.html 与 Venue→Section→RowRange 模型对齐）；实现待续：SVG 分区组件 + 场次座位配置接入

---

## 三、完全没有（无设计无实现）

- [x] 平台座位型票品模型（独立 `platform_seats`，逐座状态机，守卫条件 UPDATE 并发抢座只放行一单）— c7ea49c；PlatformSeatModuleTests 9 例（含并发抢座）
- [ ] 3D 座位图（P2）— 无设计无实现；验收：2D 过目后给出 3D 方案（2D 过目已代决 2026-10-14 巡检授权，3D 维持 P2，不阻塞 2D 实现）

---

## 挂起项（待用户裁决，不计入缺口）

- 座位图数据来源调研到立项决策 — 调研已交（dde227b），待用户拍板后才动实现
- 座位图第一阶段范围与三问验证 — 待用户指定 5 个目标场馆与人力
- ~~Phase 07 全部 UI 条目的原型过目与拍板~~ — 已代决（2026-10-14 巡检授权）：原型走查完成，13 项待办验收口径全覆盖，无阻塞缺陷；用户可事后核对推翻，推翻时按 todo 各条「已代决」注记回溯

- [x] 文档去 AI 腔（来源：用户 2026-10-14）— 12 个文档（architecture 7 / implementation-plan / deployment / design 2 / research），42 增 43 删；docs build 通过 — 23135f7
  - 改动口径：删元叙事与空洞总起句、合并首先/随后式过渡、结尾总结框改为可验证检查项、形容词换成命令与表名

- [x] 新功能：明星图鉴（来源：用户 2026-10-14）— 设计稿 docs/design/star-catalog.md（已拍板），模块 awn-star + 迁移 V18 + 公开/运营接口 + web 图鉴页面，13 后端测 + 46 前端测全绿（e454980）
  - 内容：收集**明星的介绍、照片、相关作品、类型（演员/歌手等）、国籍**，以及**其参与的作品列表**
  - 需要先定的数据模型（别急着写页面）：
    1. 人物字段：姓名、别名、**国籍**、职业类型（演员/歌手/……）、简介、照片
    2. 作品字段：名称、类型、年份
    3. **人物 ↔ 作品 的关系**：一个人参演多部作品、一部作品有多个人，是**多对多**；关系上还要能带角色名/身份（主演/配角/演唱主题曲等）
  - **素材红线（不可让步）**：**不得生成明星肖像**，也不得使用品牌 logo 代餐。照片只能来自**有授权的来源**或明确标注的占位图；**缺图就用占位，不要用生成的人脸顶替**
  - 验收：先出数据模型与页面结构（原型或设计文档），**经用户拍板后再实现**；不许直接开工写业务代码

- [x] 图鉴编辑能力（已代决 巡检 R2）— 草稿可改（PUT）可删（DELETE）、已发布冻结、改动需先 unpublish，与专场「发布版本不可变 + 撤回」同构；关系行随外键级联清除 — 4a58ab7；StarModuleTests 7 + StarApiTests 5
  - 理由：交付后发现草稿创建后无法修改或删除（只有 create/publish/bind），编辑能力设计稿 §4.3 已列为待补；属设计取舍且有明确推荐，故代决执行。分页暂不做（规模未到，且会改动刚交付的公开响应形状），仍列待拍板。
- [x] 文档与契约卫生（已代决 巡检 R2）— README 徽章同步 Spring Boot 4.1.1 / Modulith 2.1.1、star 运营写接口补入 contracts.md、删除陈旧重复 README.zh-CN.md — cbea2d9；docs build 通过
  - 理由：徽章与 pom 实际版本不符；star 运营接口交付时漏进契约；README.zh-CN.md 是 README.zh.md 的陈旧无引用子集（缺切换器与 awn-star 行），全局命名规则以 README.zh.md 为准。
- [x] 图鉴前端错误处理（已代决 巡检 R2）— api-client 非 2xx 抛 ApiError（带 status + 后端错误码），列表页不再把错误信封当数组渲染（429 / 500 时会白屏崩溃）；详情页按 404 区分「不存在或未发布」与「加载失败」 — baeffac；前端 49 例
  - 理由：交付后发现公开列表接口返回非 2xx 时，客户端把 `{code,message}` 当成功体 resolve，列表页 `persons.map` 抛 TypeError 白屏；属明确缺陷，故代决修复。

