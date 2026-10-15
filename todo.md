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
- [x] P05 卖家取消挂牌（LISTED → DELISTED；RESERVED/SOLD 不可撤，撤下后凭证行不可再下单）— a418f39（delist 域守卫 + facade）；守卫测试 `sellerDelistGuards`（撤后下单被拒 / 预留中拒绝 / 成交后拒绝 / 拒绝后无残余 / 未知挂牌 NotFound）随本提交补
  - 余项：~~`delist` 无卖家归属校验~~ 已落地（fcf33a4，`delist(listingNo, sellerNo)` 双参数 + 归属守卫 409 CONFLICT / 未知挂牌或卖家 404；MarketplaceModuleTests `delistRequiresListingOwner`）
- [x] P05 争议处理（买卖双方争议受理与裁决）— 77418bf：设计先行 `docs/architecture/dispute-handling.md`（争议单模型 / 状态机 / 裁决权限 / 边界），实现落在 awn-order 模块（waitlist 先例）+ V22__order_disputes.sql；状态机 OPEN→（受理）UNDER_REVIEW→（裁决）RESOLVED，买家可撤回未受理争议（WITHDRAWN 终态），部分唯一索引兜底同订单至多一张在途争议；开单守卫 = 订单本人 + PAID/FULFILLING/FULFILLED + REFUND 金额≤订单应付 + 凭证 URL 受控域白名单，卖家归属按凭证行快照（ListingView 增 sellerId）；裁决权限收敛在 `/api/v1/admin/disputes/**`（X-Admin-Token，买家面无任何裁决入口），裁决三种结论并留痕 decidedBy/decidedAt，裁决不动钱（退款走既有退款流、重新交付走履约面）；测试 `DisputeModuleTests` 12 例 + 错误码守卫新增 DISPUTE_NOT_FOUND / DISPUTE_CONFLICT（26 码），后端全量 268 例绿
  - 余项：~~contracts.md 争议端点段~~ 已补（5d5b55e）；~~买家争议入口 UI~~ 已落地（7bd8312，订单页争议区：开单/列表/撤回 + `lib/disputes.ts` 守卫镜像自检，演示身份读 localStorage `DEMO_USER_KEY`；测试 dispute.test.ts 6 例 + disputes.test.ts 15 例，`next build` 绿）；~~卖家回执 / 申辩随 HTTP 卖家端点落地~~（36d5fbc 契约 + fcf33a4 实现：卖家查归属争议 / 申辩（归属 + 在途 + 非平台库存守卫）/ 管理面读申辩；V24 申辩表；DisputeModuleTests 16 例）；~~运营台争议队列~~（dd47e60，`DisputesView`：列表 + 受理 + 裁决表单 + 卖家申辩按需加载；api-client 增 `DisputeResolution` / `DisputeSellerResponseView`；vitest 287 例 + admin/web 构建绿）；~~裁决 REFUND_APPROVED 后的退款执行联动~~ 已落地（2026-10-15 本轮：admin 争议队列详情内执行入口——GET /orders/{orderNo}/payments 取 SUCCEEDED 支付单、金额预填裁决金额、POST /orders/{orderNo}/refunds 幂等键随内容变化换新；api-client 补 createRefund / RefundView；裁决不动钱不变，可退余额服务端守卫 409 REFUND_CONFLICT；admin-format 退款校验 3 例 + admin-client 退款通道 4 例 + api-client createRefund 2 例，前端 310 例全绿 + admin vite build 通过）

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
- [x] 四个共享包（ui / campaign-renderer / ticket-components / api-client）— 验收：组件测试（已有 19 例，83c91e3）+ 至少一端消费；已消费：ui（web + admin）、api-client（web 全部页面 + 小程序请求层）、ticket-components（TierCard 票档卡，f348026）、campaign-renderer（web 专场页 /campaigns/{code}，56335dc）— 四包全部落地
  - 已代决（2026-10-14 巡检授权）：小程序端按运行环境用自研 Taro 子集渲染（View/Text 语义与 DOM 包不通），不算作该包消费面；`@awn/campaign-renderer` 另导出纯常量 `CAMPAIGN_SECTION_TYPES`，web 适配层（apps/web/lib/campaign-config.ts）据此剔除白名单外类型，记录 skipped 并告警
  - 新契约：api-client 补 `renderCampaign()` + `RenderedPageView`（campaign.test.ts 3 例）+ web 适配层 5 例；前端 116 例全绿，`next build` 两路由编译通过
- [x] apps/web（Next.js）接真实 API 渲染统一商品列表 — 验收：专场页渲染真实商品与可售状态 — f348026；前端 78 例 + PublicEventApiTests 16 例（含新增场次列表端点 3 例）
  - 已代决（2026-10-14 巡检授权）：① 场次可见性 = 父活动可见性（`Event::isPublic`），不单独设场次发布流（SessionStatus 留潜态）；② 可售状态由 available 派生：null→未开售 / 0→已售罄 / ≤20→余票紧张，不发明后端没有的状态；③ 后端购买规则未落地前 TierCard 不渲染限购行；④ 新增 `GET /api/v1/events/{eventId}/sessions` 公开场次列表（父活动未公开→404，按开始时间升序）供活动页选场次
  - 交付面：/ 发现列表、/events/{id} 活动页（Hero + 场次列表）、/sessions/{id} 票档页（真实商品 + TierCard 可售状态）；封面用几何占位（素材红线）
- [x] apps/admin（Vite+React）最小活动/专场编辑 + 发布流 — 验收：三视图编辑发布流 — 3cf8dda；前端 90 例 + AdminApiTests 7 例（含全状态活动列表断言）
  - 交付面：订单视图（GET /admin/orders + 状态筛选）、库存中心（池余额 + 可用量占比预警条 + 调整表单 POST /inventory/adjustments + 审计记录）、活动发布流（GET /admin/events 全状态列表，草稿可发布，重复发布 409 提示）、异常视图（未确认告警 + 确认）；令牌 X-Admin-Token 输入并 localStorage 记忆
  - 已代决（2026-10-14 巡检授权）：① 新增 `GET /api/v1/admin/events`（EventFacade.allEvents，全状态按 id 升序）作为发布流入口；② 原型的「场次配置」视图暂缓——地理/座位分区后端字段未落地（todo 125），先接的后端就接（订单/库存/发布/异常四视图），不为无后端的表单造假；表单展示列（商品名/场次名）后端未下发的不渲染
- [x] apps/miniprogram（Taro）白名单 section 子集渲染 + 下单链路 — fb4abed；前端 108 例（新增 mp-request/mp-api/order-flow/sections 37 例）+ ProductApiTests 8 例 + taro weapp 构建全 6 页 0 错
  - 交付面：发现列表 / 活动页 / 票档页（数量加减）/ 下单确认（Idempotency-Key + 后端金额重算）/ 订单支付（sandbox 渠道 + 支付历史）/ 专场页（campaign render 子集渲染）
  - 已代决（2026-10-14 巡检授权）：① section 子集取文本优先 7 类（heroBanner/richText/noticeBar/rulePanel/faq/priceTrend/countdown），媒体与数据列表类跳过并 console.warn，不强造 View/Text 渲染不了的形态；② 公共商品接口透出 `poolCode`（详情/可售摘要/场次商品列表，未建池为 null 按未开售处理）——数量行下单硬依赖，契约已补；③ 无公开专场列表接口，campaign 页手动输专场码获取，不造假入口；④ 微信拉起支付需商户号与签名（沙箱无配置），支付页显式占位说明，不伪造支付成功；⑤ userId 为演示输入（无登录集成），实名字段后端未提供、不参与请求；⑥ 请求通道抽象为 Transport 注入（Taro.request 仅在 app 启动时 setTransport），lib 层单测不依赖 @tarojs
- [x] apps/client（Flutter）从契约生成模型并跑通只读链路 — 工程上需先 `flutter create .` 生成平台工程；验收：只读活动/商品列表，原型过目已代决（2026-10-14 巡检授权）
  - 已代决（2026-10-14 巡检授权）并落地：`flutter create --platforms=android,ios`（pubspec 定位手机 App，桌面端不铺工程）；「从契约生成」以 docs/api/contracts.md + 后端记录为源手写模型、json_serializable 生成序列化（仓库无 OpenAPI 工件，contracts.md 明示）；Long 字段按 JSON 数字类型化（后端无 Long→String 序列化器，api-client 的 string 标注偏松）；API 域名 `--dart-define=AWN_API_HOST`（默认 http://localhost:8080），与小程序同名环境变量
  - 交付面：发现页（活动列表 + 下拉刷新 + 错误重试）→ 活动页（场次列表）→ 票档页（名称 + ¥单价 + 可售状态），可售派生与 web/小程序同口径（null 未开售 / 0 售罄 / ≤20 余票紧张）；只读终点，下单支付待购买页全流程接入
  - 测试：flutter analyze 0 issues；flutter test 16/16（模型解析 5 + 可售派生 4 + 客户端含错误信封 5 + 只读链路 widget 走查 2，传输层 StubHttpClientAdapter 不触网）

### 其他设计未落地
- [x] 情报采集调度器（`collectAll` 入口 + worker 按 `intelligence-collection-ms` 周期执行，停用来源跳过，单源失败不中断整轮）— 44c6094；IntelligenceCollectionTaskTest 2 例
- [x] Redis 限流（按资源组限流，超限 429 RATE_LIMITED，存储 memory/redis 可选，限流组件故障 fail-open）— eeee7a3；限流相关 11 例
- [x] 运营后台前端三视图 — 同 apps/admin 条目 — 3cf8dda；订单 / 库存（调整 + 审计）/ 活动发布流（+异常视图），场次配置视图随 todo 125 地理后端落地后接入
- [ ] ErrorResponse 字段级校验明细（`fieldErrors`）— 已代决（2026-10-14 巡检授权）：维持暂缓。理由：现行 `ErrorResponse(code, message, traceId)` 已可定位错误；四端原型均用内联 `.hint` 提示，无字段级错误展示诉求；待真正出现多端复用字段校验报错的需求再引入
  - 复核（2026-10-16 PATROL1016）：解锁条件仍未触发——`ErrorResponse.java:7` 仍为三字段 (code, message, traceId)；其后新增的全部表单（web 结算观演人 / admin 场馆配置 / admin 退款执行 / 铺座）校验均为「自检函数返回首条文案 + 内联提示」单条模式，无任何端消费字段级错误。维持卡点，待真实场景。
- [x] Phase 07 四端 UI 原型重做与多主题体系（web/admin/client/miniprogram）— 已代决（2026-10-14 巡检授权）原型过目：四端原型 + ui-system.md 走查完成，对照 13 项待办验收口径全覆盖，无阻塞缺陷；素材红线：不生成明星肖像/照片/logo 代餐，敏感信息用占位数据 — 多主题体系交付面 web（5caad72）+ admin（1f3ed6a，ThemeSwitcher.test.tsx + themes.test.ts 随包测试）
  - 已代决（2026-10-14 巡检授权）并落地（5caad72）：web 多主题切换——`@awn/ui` 主题包（11 套主题数据取自 prototypes/theme.css，18 个 `--t-*` 语义令牌 + `--awn-*`→`--t-*` 桥接，既有组件零改名跟随主题；default 桥接值 ≡ 现行品牌令牌，测试断言）；layout 注入全量主题块并固定 `data-theme="default"`（移除原 inline 注入——inline 样式会压过 `[data-theme]` 块）；右下角悬浮切换器（原型 .theme-picker，11 按钮 aria-pressed + localStorage 记忆）；首帧默认主题、水合后按记忆切换（localStorage 仅客户端，闪变代决接受）
  - 已代决（2026-10-14 巡检授权）并落地（1f3ed6a）：admin 同包接入——main.tsx 渲染前同步注入主题块并应用记忆主题（纯 CSR 无闪变）；顺带修复潜在缺陷：admin 此前从未注入 AWN_CSS_VARS，`--awn-*` 全部未定义（页面靠字面色值硬撑）；短标签下沉 `@awn/ui`（AWN_THEME_SHORT_LABELS）两端共用
  - 已代决（2026-10-14 巡检授权）：移动两端不铺 11 套主题——小程序 WXSS 只有 page 级变量且品牌值固化在 app.css（运行时切换需注入整套变量 + 自建存储，节日主题在小程序场景价值低）；Flutter 无 CSS，主题化 = ThemeData 端口（现字面色值于 main.dart）；两端如需暗色另行单项推进，多主题体系（数据 + 切换器 + 桥接）以 web/admin 为交付面
- [x] 活动页模板 + 三套实例（F1 / 周杰伦 / 周传雄）— 原型过目已代决（2026-10-14 巡检授权）；模板区块①Hero + ③场次列表已接真实 API（f348026），区块②演出信息 / ④票档 / ⑤须知按内联展示形态接入（c79f002 + 906b51d）；三套实例已落地（b29b8cc）
  - 已代决（2026-10-14 巡检授权）并落地（c48bacf）：web 专场页补解析层——后端存的 props 是引用型（eventIds / eventId / ruleDocumentId / faqId / assetIds），`@awn/campaign-renderer` 读的是展示型（sessions / tiers / paragraphs / until / rules），此前直传导致真实已发布专场渲染成空白区块；现按引用查公开 API（场次 / 票档）组装展示数据，富文本取服务端 content 纯文本分段；无公开数据源的模块（rulePanel / faq / imageGallery / priceTrend / ticketComparison / channelComparison）显式标记「待接入」并写明原因，不渲染空白区块也不造假内容；引用不到（404 或模板占位 ID）按待接入处理，网络类异常照常抛出
  - 已代决（2026-10-15 巡检）并落地：须知 / 常见问题 / 图片三模块认内联展示型 props 双形态透传——rulePanel 认 `rules` 文本数组（引用型 ruleDocumentId 无可查端点，悬空引用继续待接入）、faq 认 `entries` 问答对（question / answer 口径，与渲染包一致）、imageGallery 认 `images` 含 alt 条目（src 缺失走占位块，素材红线）；键存在但形态不符标记「格式不符」待接入；小程序 faq 键 q/a 对齐为 question / answer（两端口径统一）
  - 后端翻面与深校验已落地（c79f002）：预置模板 motorsport/concert 下发内联展示形态（rulePanel `rules` 文本数组、faq `entries` 问答对），不再下发无可查端点的引用 ID（ruleDocumentId / faqId）；渲染器 rulePanel/faq 逐条转义出 HTML（文本不裸拼）；校验器递归到任意深度——嵌套对象/数组里的 script/iframe/object/embed 与 URL 键（images[].src 等）白名单域同口径拦截，数组字符串项补脚本检查；campaign-rendering.md 示例同步为内联形态；测试 CampaignRenderingTests 8→11（嵌套脚本拒、数组字符串项脚本拒、嵌套非白名单域拒、内联形态合法）+ CampaignModuleTests 5 回归 + 后端全套 verify 234 例全绿
  - 三套实例已落地（b29b8cc）：scripts/seed/demo-campaigns.mjs 三套内容逐字取已批准原型（activity-f1 / activity-jay / activity-zcx，demo-note 标注「数据为示例」）——heroBanner 标题副标 / 演出信息 / 场馆与入场 / 购票须知 / 退换规则 / 座位图入口按原型逐块配置；素材红线守住：无 imageGallery / videoEmbed 区块与任何媒体引用键（url / src / poster / assetId），视觉一律文字占位；scripts/seed-demo-campaigns.mjs 种子脚本走 create → draft → validate → publish（X-Admin-Token 鉴权，AWN_API_BASE / AWN_ADMIN_TOKEN 可配，--dry-run 打印载荷，专场已存在则复用并重新发布，幂等）；场次列表 / 票档区块引用占位活动 id '0'（不存在）——resolve 层按待接入渲染并说明原因、不造假数据，运营在目录创建真实活动后回填真实 eventId 再 draft → validate → publish；campaign-rendering.md 补「预置实例与种子脚本」一节
  - 测试与口径修正：scripts/seed/demo-campaigns.test.mjs 8 例（三实例码与标题对照原型、原型出处文件存在性、区块类型白名单 + id 唯一 + schemaVersion 服务端校验器镜像、素材红线无媒体键、script / iframe 禁词镜像、各区块展示 props 非空、占位活动引用 '0' 明示）+ campaign-resolve 待接入中文标签补 eventList「场次列表」/ ticketGrid「票档」（长期待接入区块不回落英文类型名）1 例——前端 253 例全绿 + next build 通过 + docs build 通过；原余留口径修正：公开专场列表接口已存在（GET /api/v1/campaigns，只含 PUBLISHED，contracts.md「专场列表与内容接口」），发布后的三套实例会进入公开列表
- [x] web 首页专场列表（自定位条目 2026-10-15：GET /api/v1/campaigns 已存在且 contracts.md 已载而无端消费，三套实例种子（b29b8cc）发布后缺发现入口；原型 web/home.html 即活动列表页）— 已落地（05ade03）：apps/web/components/CampaignList.tsx 卡片列表（目的地 /campaigns/{code}，封面文字占位守素材红线，subtitle 缺省不渲染空行）+ 首页「专场」区（活动目录与专场两个数据源独立加载，专场失败只降级专场区不影响活动列表；无已发布专场不出区块不造假入口）；测试 CampaignList.test.tsx 4 例（卡片链接与文案 / 空列表不渲染 / 缺 subtitle 无空行 / 无 img 素材红线），前端 257 例全绿 + next build 通过
- [x] 购买页全流程（选场次 到 票档/座位 到 数量实名 到 下单 到 支付 到 出票）— 原型过目已代决（2026-10-14 巡检授权）；三端购买流已接：web 结算 + 订单支付页（a0d8380）、小程序下单支付链（fb4abed）；客户端（Flutter）按只读范围未含下单（21d1536）
  - 余项拆解：~~座位选区~~ 已落地（0cfcb69，见 2D 座位图条目）；~~观演人实名后端契约~~ 已落地（2026-10-15 R1015D：V25 order_attendees + 行级 attendees 命令（数量=quantity 一票一证）+ 同场次同证件唯一（行内/跨订单校验 + ux_order_attendees_session_idno 兜底转 409）+ 读面脱敏（留前 3 后 4）+ 幂等指纹含实名 + contracts.md「观演人实名」节；OrderCoreTests 16 例含实名 7 例）；~~web 结算页观演人表单 + api-client 类型透出~~ 已落地（2026-10-15 本轮）：api-client 透出 `AttendeePayload` / `AttendeeView` + `OrderLinePayload.attendees`（可选，一票一证）+ `OrderItemView.attendees`（服务端脱敏值）；web 结算页逐票观演人表单（数量行按票档、座位行按「票档:座位号」逐座；实名可选填、不填整行发 null——空数组会被后端按数量不符 400；同场次同证件前端先挡、后端 409 ORDER_CONFLICT 给可读提示）、报价行不带实名（金额与实名无关）、订单页展示服务端下发脱敏实名；测试 checkout.test.ts 实名 11 例 + api-client create-order 3 例，前端 301 例全绿 + next build / admin vite build 通过
  - 交付面（web）：/sessions/{id}「去选票」→ /checkout（票档数量加减 + 服务端报价 + 幂等键下单）→ /orders/{orderNo}（订单状态 + 支付发起 + 支付记录 + 取消订单 + 履约出票态）
  - 已代决（2026-10-14 巡检授权）：① 金额一律取服务端报价（POST /checkout/quote 不扣库存），下单时后端重算并原子预留，前端只显示预估值；② 幂等键在一次结算尝试内固定、改购物车即换新键（同键不同内容后端判冲突）；③ 支付成功只认渠道回调，网页端不凭参数确认成功，渠道结果未知（UNKNOWN）显示「待核实」且禁止再次发起支付防重复扣款；④ 超时关单 ≠ 支付失败，订单状态只取后端结论、不前端推断；⑤ 座位选区与实名信息后端契约没有，页面显式说明不渲染假表单（座位图见 todo 126）；⑥ 无登录态，userId 为演示输入并本地记忆；⑦ 履约「未出票」以 GET /orders/{id}/fulfillment 的 404 为正常未出票态，不当错误
  - 测试：新增 `apps/web/lib/checkout.test.ts`（购物车加减与上限、下单行映射、自检文案、订单/支付/履约状态文案与动作门槛、金额格式化）+ api-client 补 `getFulfillment` + 前端 146 例全绿 + `next build` 类型与 lint 通过 + 后端契约回归 CheckoutApiTests 6 / OrderApiTests 7 / PaymentApiTests 10
  - 排队 / 候补 + 支付失败重试与换渠道 + 订单超时倒计时已落地（2026-10-15 巡检授权，契约先行）：contracts.md 补三节——订单支付倒计时（`expiresAt` / `serverTime` / `payExpiresInSeconds`，倒计时以 serverTime 为锚点本机推算，是否超时只认后端结论，非待支付恒 0）、支付失败重试与换渠道（`retryable` 语义 + `GET /payments/channels` 渠道编码由后端下发不硬编码 + `POST /payments/{paymentNo}/retry` 守卫表：未知 404 / 源单非失败 409 / 订单非待支付或过截止期 409，金额币种仍取订单快照、原单保持 FAILED 历史不改写）、排队 / 候补（四端点 + WAITING/PROMOTED/CLAIMED/EXPIRED/CANCELLED 状态机表 + Worker 驱动口径）
  - 后端（576cf44）：OrderView 补 serverTime + payExpiresInSeconds；PaymentView 补 retryable、PaymentFacade 补 channels() 与 retryPayment()；V20__waitlist.sql + WaitlistStatus/WaitlistEntry/WaitlistService/WaitlistRepository（守卫迁移 + bindOrder + pg_advisory_xact_lock 判重）+ WaitlistFacade/Controller/Worker 递补任务（awn.worker.tasks.waitlist-promotion 默认关闭）+ WAITLIST_NOT_FOUND/WAITLIST_CONFLICT 错误码入表；递补抢占→建单→绑单同一 TransactionTemplate（库存预留失败整条回滚席位保持 WAITING；建单幂等键取候补编号派生值重跑返回同一订单不重复预留）；排队不占库存不预生成订单，位次按同池 WAITING（created_at, id）派生不落库；候补递补订单与普通下单同源同倒计时口径
  - 前端（web，只接状态展示不造假入口）：api-client 补三块契约类型与调用（含候补全端点）；订单页倒计时以 serverTime 为锚点每秒重算、状态推进即停计时；渠道清单改由 GET /payments/channels 下发（清单不可用禁用发起支付并明示，不硬编码兜底）；失败单 retryable 为真时按钮切「重新支付」走 retry 端点可换渠道；候补入口暂不建 UI——无登录态且超出「只接状态展示」，api-client 契约齐备待登录态落地后铺；小程序 / Flutter 只读面本轮未接
  - 测试：新增 WaitlistModuleTests 14 例 + PurchaseFlowApiTests 11 例 + ApiExceptionHandlerTests 错误码守卫 22→24；checkout.test.ts 补倒计时（锚点推算 / 归零 / 不可解析兜底 / 格式）与可重试门槛（retryable 与状态双重判定）共 8 例；后端全套 verify 246 例全绿 + 前端 206 例全绿 + next build 通过；测试基建顺手收紧：所有测试上下文共享同一 PostgreSQL 容器，Hikari 按主配置 20 预热 × 多缓存上下文曾打满 max_connections（PSQL too many clients），AbstractPostgresIntegrationTest 收紧为 maximum-pool-size 6 / minimum-idle 1
- [x] 每场次地理位置（城市/地址/坐标 + 地图）— 原型过目已代决（2026-10-14 巡检授权）；后端模块已落地（8323788）：V19 迁移（venue 坐标列 + session venue_id 外键/索引 + session 直配 geo 列 + 城市索引）、Venue 坐标成对与范围守卫（单给坐标/越界一律拒）、EventFacade 场馆查询/全场次列表/sessionVenue/attach/detach 与继承解析（场次场馆覆盖 → 父活动场馆 → null 不造假坐标）、VENUE_NOT_FOUND 错误码接异常处理器并同步 contracts.md 错误码表；测试 VenueGeoModuleTests 8 例 + 后端全套 verify 227 例全绿
  - 公开读透出 + web 展示已落地（75865ae）：publicSession/publicSessions 组装 geo（场次场馆覆盖 → 父活动场馆 → null，全空场馆不渲染假地点），contracts.md 补 geo 口径（坐标缺失为 null，客户端不得按 (0,0) 兜底）；api-client SessionView 加 geo；web 活动页场次卡与场次页头部渲染 geo-row（GeoRow 组件，样式 f348026 已备，场馆名/城市/地址非空项「·」连接）；测试 VenueGeoModuleTests 8→12 + PublicEventApiTests 16 回归 + 前端 198 例全绿 + next build 通过
  - 运营配置面已落地（01bdff4）：管理面端点 GET /admin/venues、GET /admin/events/{eventCode}/sessions（allSessions 组装解析 geo）、POST/DELETE /admin/sessions/{sessionCode}/venue（挂/摘，摘除回退继承父活动场馆，未知场次/场馆 404 SESSION_NOT_FOUND/VENUE_NOT_FOUND）+ contracts.md 运营接口条目；admin 第五视图「场次配置」（选活动 → 场次表含当前 geo → 场馆下拉挂/摘，geo 空显示「—」不造假地点）；测试 AdminApiTests 7→8（venue 管理全流：鉴权 401、列表、挂/摘回退、404 信封）+ VenueGeoModuleTests 12 回归 + 前端 201 例全绿 + admin vite build 通过
  - 整条地理链（存储 / 守卫 / 公开读 / web 展示 / 运营配置）已闭环；交互地图（缩放 / 路线 / 精确到门牌）按 R1015F 裁决暂不启动，等真实需求与 SDK 拍板再议
  - 地图方案已交（docs/design/map-display-plan.md，2026-10-15）：建议走「首方静态图」（运营传图片 + 前端坐标落标记，不引第三方 SDK、不拍板服务商），契约预估 V25 map_image_url（存储域白名单）+ SessionVenueView.mapImageUrl + GeoRow 升级（无图回退现状文字行）；待裁决三点——是否接受「示意图」口径 / 是否需要交互地图（需则拍板 SDK）/ 图片由谁提供
  - 地图展示已落地（2026-10-15，R1015F 授权：接受「示意图」口径；交互地图与供图方式待真实需求再议）：V26__venue_map_image.sql（预估 V25 已被 order_attendees 占用）+ 标记改为运营随图给定百分比（0-100 成对守卫——示意图无地理范围，经纬度不能线性映射）；VenueView/SessionVenueView 透出 mapImageUrl/mapMarkerX/mapMarkerY 随 geo 继承链 + StorageUrlValidator 域白名单（拒绝 400 VALIDATION_FAILED）+ PUT /admin/venues/{venueCode} 全量覆盖（半截坐标/半截标记/越界一律 400，未知场馆 404 VENUE_NOT_FOUND）+ admin「场次配置」场馆配置卡片（全量覆盖口径表单上方明示）+ web GeoRow 图/标记/文字行（onError 回落，无图行为与现状一致，根节点保持 span 适配 Link 内使用）；测试 VenueGeoModuleTests 12→17 + AdminApiTests 7→9 + 前端 geo-image 4 例 / GeoRow 6 例 / admin 契约 8 例全绿 + 后端全套 verify 298 例 + 前端 328 例 + web/admin 构建通过
- [x] 2D 座位图（P1）— 原型过目已代决（2026-10-14 巡检授权，seatmap-2d.html 与 Venue→Section→RowRange 模型对齐）；公开链路已落地（854a77b）：后端 GET /api/v1/sessions/{sessionId}/seatmap（复用 PlatformSeatService.seatsOfSession 经 InventoryFacade 透出，可见性与场次详情同口径：未知场次或父活动未公开一律 404 SESSION_NOT_FOUND；公开载荷剥去 orderNo/sessionCode，占座订单号不进公开面，productCode 保留供票档归位）+ contracts.md 条目 + api-client getSeatmap/SeatMapItem + /sessions/{id} 座位型商品选座区（座位型 = 座位图载荷中出现过的商品编码，不发明字段；SeatMap 分组渲染 + 选择收集）；测试 SeatmapApiTests 4 例（真实 PG）+ PlatformSeatModuleTests 9 例回归 + 前端 195 例全绿 + next build 通过；余项已清（0cfcb69）：铺座运营入口与选座下单契约——订单行第三路径座位型（V23 seat_no 列 + ck_order_item_source 三分支互斥，OrderLineCommand.seatLine 一行一座 quantity=1），下单/取消/超时/支付确认全链座位状态机联动（reserveSeatForOrder 商品归属校验 409 INVENTORY_CONFLICT、未知座位 404 SEAT_NOT_FOUND 新错误码），管理面 POST/GET /api/v1/admin/sessions/{sessionCode}/seats 铺座批次（重复位置/未知商品整体失败）+ admin 第六视图「铺座」（分区,排,座 逐行粘贴解析，坏行带行号报错不静默丢弃），web 选座→结算打通（sessionStorage 按场次暂存选区带入 /checkout，每座独立成行提交，下单成功清除；座位冲突/失效按服务端结论引导回座位图重选）；测试 SeatOrderModuleTests 8 例（含并发抢座只放一单）+ SeatAdminApiTests 4 例 + 前端 244 例全绿 + next/vite build 通过
  - 前端组件已落地（b607772）：数据层（lib/seatmap.ts 分组/排序/可选性）+ SVG 组件（components/SeatMap.tsx，三态图例与原型一致：可选/已售置灰/本次选中，aria-label 区分五态，Enter/键盘可选）+ 11 例（分组排序 5 + 渲染交互 6）
  - 已代决（2026-10-14 巡检授权）：platform_seats 只有分区/排/座编号无坐标 → 程序化网格排布，不臆造场馆几何（弧形/朝向需空间数据源，属挂起调研项）；舞台位置与朝向不声称正确，只标方向提示

---

## 三、完全没有（无设计无实现）

- [x] 平台座位型票品模型（独立 `platform_seats`，逐座状态机，守卫条件 UPDATE 并发抢座只放行一单）— c7ea49c；PlatformSeatModuleTests 9 例（含并发抢座）
- [ ] 3D 座位图（P2）— 方案已交、S1 已落地（见下方两行）；**卡点：S2 起依赖座位坐标数据，坐标来源与首批场馆名单未拍板（见挂起项），拍板前不动实现**。验收：2D 过目后给出 3D 方案（2D 过目已代决 2026-10-14 巡检授权，3D 维持 P2，不阻塞 2D 实现）
  - 3D 方案已交（docs/design/seatmap-3d-plan.md，2026-10-15）：定位 2D 的视觉增强层（交互/无障碍仍走 2D 路径）；分四阶段——S1 视角增强（无坐标无新依赖，CSS 3D 变换现有 SVG 网格）、S2 坐标可选化契约（SeatView 可选 x/y，null 回退网格）、S3 真 3D 原型（three.js 程序化，参考 Fi-Stadium InstancedMesh+Raycaster 思路，吸收思想不引代码）、S4 L1/L2 选区选排（依三问验证）；几何线框占位不违反素材红线；按 todo 口径设计文档不记为已完成，实现未动
  - S1 已落地（2026-10-15）：SeatMap 增「俯视 / 透视」视角切换（aria-pressed 双按钮 + CSS perspective/rotateX 纯变换，不改节点数与交互层）；默认俯视与原 2D 渲染完全一致；prefers-reduced-motion 禁用过渡；选座/键盘交互回归无变化（组件测试 5→7 例，前端全量 190 例绿）；S2 起依赖坐标数据（挂起项拍板后）
  - 复核（2026-10-16 PATROL1016）：解锁条件仍未触发——repo 内无场馆名单、无数据来源拍板记录（venue-seatmaps.md §8 开放项 #1 vendor/自建、#2 许可、#4 首批名单、#7 商业数据、#8 编辑器排班均无裁决）；座位坐标数据未落地（platform_seats 仍只有分区/排/座编号，无 x/y 列）。维持卡点，待拍板。
  - 立项拍板已代决（2026-10-16，FW1016D）：自建 venue-seats（Apache-2.0）、编辑器 AWN 自建，见 venue-seatmaps.md §8；坐标数据落地仍缺 #4 首批场馆名单与人力（点名清单不代决），S2 维持卡点。

---

## 挂起项 / 卡点（只缺用户裁决或数据，不计入缺口）

- 座位图数据来源调研到立项决策 — **已代决（2026-10-16，巡检单 FW1016D 授权，用户可推翻）：自建 venue-seats + Apache-2.0 + 编辑器 AWN 自建**；理由见 venue-seatmaps.md §8 #1/#2/#8。采购商业数据（§8 #7）涉资金与法务，不代决，仍列卡点；验证批次（§8 #3）与抽独立项目的决策仍按「三问成立」门槛执行
- 座位图第一阶段范围与三问验证 — **卡点：缺首批 5 场馆名单与人力**；名单属用户点名清单（FW1016D 明示不代决），名单与人力给出即可开工（3D 方案 S2 起同样依赖此项）
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
  - 理由：交付后发现草稿创建后无法修改或删除（只有 create/publish/bind），编辑能力设计稿 §4.3 已列为待补；属设计取舍且有明确推荐，故代决执行。分页**已代决（2026-10-15，R1015F 授权）：不分页，列表返回全量**，待真实规模需要再补（用户可推翻）。
- [x] 文档与契约卫生（已代决 巡检 R2）— README 徽章同步 Spring Boot 4.1.1 / Modulith 2.1.1、star 运营写接口补入 contracts.md、删除陈旧重复 README.zh-CN.md — cbea2d9；docs build 通过
  - 理由：徽章与 pom 实际版本不符；star 运营接口交付时漏进契约；README.zh-CN.md 是 README.zh.md 的陈旧无引用子集（缺切换器与 awn-star 行），全局命名规则以 README.zh.md 为准。
- [x] 图鉴前端错误处理（已代决 巡检 R2）— api-client 非 2xx 抛 ApiError（带 status + 后端错误码），列表页不再把错误信封当数组渲染（429 / 500 时会白屏崩溃）；详情页按 404 区分「不存在或未发布」与「加载失败」 — baeffac；前端 49 例
  - 理由：交付后发现公开列表接口返回非 2xx 时，客户端把 `{code,message}` 当成功体 resolve，列表页 `persons.map` 抛 TypeError 白屏；属明确缺陷，故代决修复。

