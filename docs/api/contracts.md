# API 契约

所有业务接口统一使用 `/api/v1`。契约以本文档 + `packages/api-client` 共享类型为准；仓库当前无 OpenAPI 工件（未引入 springdoc，无 openapi.yaml/json），前后端按本文档与共享类型开发。

## 错误信封与错误码

所有非 2xx 响应使用统一信封，禁止返回裸堆栈：

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "order not found: ORD-1",
  "traceId": "3f9c2a…"
}
```

- `code`：机器可读错误码（下表），客户端据此分支处理；`message`：人可读描述；
  `traceId`：请求追踪 ID，用于日志关联与排障（可能为空）。
- Bean Validation 失败返回固定 `message: "validation failed"` 与 `fieldErrors`；
  `fieldErrors` 键为字段名、值为该字段首条校验文案，同一字段多条错误只保留一条。
  其他错误返回空 `fieldErrors`。
- 未预期的服务器异常统一返回 `INTERNAL_ERROR` 与固定文案，**不向客户端透出任何内部信息**，
  堆栈只进服务端日志（按 `traceId` 关联）。

| code | HTTP | 触发场景 |
|------|------|----------|
| VALIDATION_FAILED | 400 | 参数缺失、格式非法、请求体不可解析 |
| PAYMENT_CALLBACK_REJECTED | 400 | 支付回调验签或字段校验失败 |
| NOT_FOUND | 404 | 资源不存在（通用） |
| ORDER_NOT_FOUND | 404 | 订单不存在 |
| INVENTORY_POOL_NOT_FOUND | 404 | 库存池不存在 |
| EVENT_NOT_FOUND | 404 | 活动不存在 |
| SESSION_NOT_FOUND | 404 | 场次不存在 |
| VENUE_NOT_FOUND | 404 | 场馆不存在 |
| CAMPAIGN_NOT_FOUND | 404 | 专场不存在 |
| PAYMENT_NOT_FOUND | 404 | 支付单不存在 |
| REFUND_NOT_FOUND | 404 | 退款单不存在 |
| PERSON_NOT_FOUND | 404 | 人物不存在或未发布（明星图鉴） |
| WORK_NOT_FOUND | 404 | 作品不存在或未发布（明星图鉴） |
| ORDER_CONFLICT | 409 | 订单状态机冲突（非法迁移） |
| IDEMPOTENCY_CONFLICT | 409 | 幂等键对应了不同的请求内容 |
| WAITLIST_NOT_FOUND | 404 | 候补条目不存在 |
| INSUFFICIENT_INVENTORY | 409 | 库存不足 |
| INVENTORY_CONFLICT | 409 | 预留状态机冲突（如确认已释放的预留） |
| WAITLIST_CONFLICT | 409 | 候补状态机冲突（重复排队、对已递补席位操作） |
| PAYMENT_CONFLICT | 409 | 支付状态机冲突（如重复确认已成功的支付） |
| REFUND_CONFLICT | 409 | 退款状态机冲突或超退 |
| DISPUTE_NOT_FOUND | 404 | 争议单不存在 |
| DISPUTE_CONFLICT | 409 | 争议状态机冲突（非下单人、订单状态不可受理、重复受理、非受理人撤回、重复裁决） |
| SEAT_NOT_FOUND | 404 | 平台座位不存在（座位型订单行引用未知座位） |
| CONFLICT | 409 | 其他业务冲突（通用） |
| RATE_LIMITED | 429 | 请求过于频繁（管理面限流，见「鉴权」节） |
| INTERNAL_ERROR | 500 | 未预期异常 |

错误码与 HTTP 状态的映射由后端唯一维护（`site.awn.shared.error`），各端不得自行推断。

## 鉴权

运营接口要求请求头 `X-Admin-Token`，作用域：

- `/api/v1/admin/**` 全部方法；
- `/api/v1/campaigns/**` 的非 GET 方法（专场管理写操作）；
- `/api/v1/campaigns/**` 的 GET 为公开读，无需令牌。

令牌校验失败返回 401，响应体为 `{"code":"UNAUTHORIZED","message":"admin token required"}`（手写 JSON，不含 traceId，`UNAUTHORIZED` 不在错误码表中——属已知偏差，待统一到异常处理器）。

令牌校验通过后按资源组限流（`/api/v1/{resource}`，默认 100 次/60s）：超限返回 429，响应体为
`{"code":"RATE_LIMITED","message":"rate limit exceeded, retry later"}`（同样为手写 JSON，不含 traceId）。
未过鉴权的请求不计入额度；限流组件故障时放行（fail-open），不阻断管理面。

## 公共接口

以下端点已暴露 HTTP：

```text
GET /api/v1/products/{productId}
GET /api/v1/products/{productId}/availability
GET /api/v1/sessions/{sessionId}/products
GET /api/v1/sessions/{sessionId}/seatmap
GET /api/v1/campaigns
GET /api/v1/campaigns/{campaignCode}
GET /api/v1/campaigns/{campaignCode}/published
GET /api/v1/campaigns/{campaignCode}/render
GET /api/v1/campaigns/{campaignCode}/rules
GET /api/v1/campaigns/{campaignCode}/faq
GET /api/v1/campaigns/{campaignCode}/assets

POST /api/v1/orders
GET  /api/v1/orders/{orderId}
POST /api/v1/orders/{orderId}/cancel

POST /api/v1/orders/{orderId}/payments
GET  /api/v1/orders/{orderId}/payments
POST /api/v1/orders/{orderId}/refunds
GET  /api/v1/orders/{orderId}/fulfillment

GET  /api/v1/payments/channels
POST /api/v1/payments/{paymentNo}/retry

POST /api/v1/waitlist
GET  /api/v1/waitlist/{waitlistNo}
POST /api/v1/waitlist/{waitlistNo}/cancel
GET  /api/v1/waitlist?userId=

POST /api/v1/checkout/quote

GET /api/v1/events
GET /api/v1/events/{eventId}
GET /api/v1/sessions/{sessionId}
GET /api/v1/intelligence/events/{eventId}/prices

GET /api/v1/stars
GET /api/v1/stars/{personId}
GET /api/v1/works
GET /api/v1/works/{workId}
```

明星图鉴（`/stars`、`/works`）是独立入口，不嵌在活动与购票链路里：只暴露 `PUBLISHED` 内容，未发布与不存在一律 404（`PERSON_NOT_FOUND` / `WORK_NOT_FOUND`），草稿不泄露。检索为基础匹配——`/stars` 的 `q` 命中姓名、原名、别名（大小写不敏感包含），另可按 `profession`、`nationality` 过滤；`/works` 的 `q` 命中标题，可按 `type` 过滤。不做全文与拼音模糊。

人物与作品是多对多：`/stars/{personId}` 的 `works[]` 与 `/works/{workId}` 的 `cast[]` 是同一份关系结构（`roleType` / `roleName` / `billingOrder`），两端据此互跳。`photoUrl` 仅在有授权来源时透出，为 `null` 时前端渲染占位图。

活动与场次为公开只读，但只暴露对公众可见的内容：

- `GET /api/v1/events` 仅返回 `PUBLISHED` / `COMPLETED` 活动，按开始时间倒序；
- `GET /api/v1/events/{eventId}` 与 `GET /api/v1/sessions/{sessionId}` 对未知资源、以及父活动未发布（草稿）的场次一律 404，不泄露草稿内容；
- `GET /api/v1/sessions/{sessionId}/seatmap` 返回场次座位图条目（`seatNo` / `productCode` / `section` / `row` / `seat` / `status`），可见性与场次详情同口径（父活动未公开 → 404 `SESSION_NOT_FOUND`）；`status` 为逐座状态机 `IN_STOCK` / `RESERVED` / `SOLD` / `DELIVERED` / `INVALID`，占座订单号不进公开面；图上「可选」仅为数据标记，实际可售以下单时后端原子预留为准；
- 场次公开读（详情与列表）响应带 `geo`（场馆派生：场次指定场馆优先，未指定继承父活动场馆；无任何场馆为 `null`）。坐标缺失为 `null` 是正常态，客户端不得按 (0,0) 或占位坐标兜底；`geo` 另带位置示意图三字段（`mapImageUrl` / `mapMarkerX` / `mapMarkerY`，可各自缺省）：`mapImageUrl` 是运营提供的**非精确示意图**（首方静态图，域白名单校验，不接第三方地图 SDK），`mapMarkerX`/`mapMarkerY` 为图上标记位置（相对图片左上角的百分比 0-100，**成对出现**，与图片可各自缺省）；示意图没有地理范围，经纬度与图上位置无换算关系，客户端不得自行猜标记位置；图加载失败回退文字行，不报错占位；
- `GET /api/v1/intelligence/events/{eventId}/prices` 返回该活动下**所有场次**的价格历史（按观测时间倒序，每条可溯源）；活动未知或未公开 → 404；场次无价格数据时返回空数组。

活动发布（`DRAFT` → `PUBLISHED`）是运营动作，经 `X-Admin-Token` 鉴权暴露：`POST /api/v1/admin/events/{eventCode}/publish`（见「运营接口」）；仅草稿可发布，重复发布 → 409 `CONFLICT`，未知活动 → 404 `EVENT_NOT_FOUND`。

公共商品接口的库存摘要为展示口径（当前直查数据库，未接缓存），**下单时必须重新验证**。商品详情、可售摘要与场次商品列表均透出 `poolCode`（未建池为 `null`，按未开售处理）：`POST /orders` 数量行需要它，未建池商品无法拼出合法订单行。

## 专场列表与内容接口

专场是运营配置的聚合页：区块配置走 `POST /campaigns/{code}/draft` + 发布版本快照（不可变 revision），
须知 / 常见问题 / 素材三类内容走本节的独立内容端点。前端不拼内容、不造内容，查到什么渲染什么。

- `GET /api/v1/campaigns`：公开专场列表，**只含 `PUBLISHED`**（草稿 / 已撤回 / 已归档一律不出现），
  按最近变更倒序（发布与草稿改动都会刷新该时间戳）。条目为专场视图
  （`campaignCode` / `title` / `subtitle` / `status` / `currentRevisionId`），列表项不再重复渲染配置。
- `GET /api/v1/campaigns/{campaignCode}/rules`：须知条目 `[{"id","title","content"}]`，
  按运营录入顺序（`display_order`，同序按 id）。`content` 为纯文本，段落以空行分隔。
- `GET /api/v1/campaigns/{campaignCode}/faq`：常见问题 `[{"id","question","answer"}]`，同一排序口径。
- `GET /api/v1/campaigns/{campaignCode}/assets`：素材 `[{"id","assetType","url","altText","displayOrder"}]`，
  `assetType` 为 `IMAGE` / `VIDEO` / `BANNER` / `THUMBNAIL`，`altText` 可为 `null`。
- 可见性：三个内容端点与 `/render` 同口径，只读**已发布**专场；专场未知或未发布 → 404
  `CAMPAIGN_NOT_FOUND`，草稿内容不泄露（与活动 / 场次 / 图鉴的公开读一致）。

引用型 ID 收口：区块 props 里的 `ruleDocumentId` / `faqId` / `assetIds` 按上述列表的 `id` 匹配解析，
匹配不到即视为查不到（标记待接入，不渲染空白区块）；内联展示型 props（`rules` 文本数组 /
`entries` 问答对 / `images` 条目）优先于引用。

素材红线：`url` 只能指向有授权来源或明确标注的占位图，**不得由内容端点生成人像或用 logo 代餐**；
`altText` 缺失时前端出占位块。

## 交易接口

以下接口已暴露 HTTP：

- `POST /api/v1/checkout/quote`：结算报价。请求体与创建订单同构（不含幂等键与用户），
  按订单行同一服务端口径计算金额；**报价不扣减任何库存、不产生订单**，
  下单时金额重新计算并逐行原子预留。非法行（负行合计 / 多来源或缺一来源 / 非正数量 / 座位型行数量非 1）→ 400 `VALIDATION_FAILED`。

订单行来源三选一（`ck_order_item_source` 数据库兜底互斥）：

- **数量型**：`poolCode` 必填 + `quantity`，向库存池原子预留（`reservationNo` 回填）；
- **凭证型**：`listingNo` 必填且 `quantity` 必须为 1，向挂牌独立票品预留（`unitNo` 回填），不触碰库存池；
- **座位型**：`seatNo` 必填且 `quantity` 必须为 1，向平台座位（`platform_seats`，逐座状态机）预留，
  不触碰库存池与挂牌。座位不存在 → 404 `SEAT_NOT_FOUND`；座位不属于订单所引用商品 → 409
  `INVENTORY_CONFLICT`。取消 / 超时释放座位回 `IN_STOCK`，支付确认转 `SOLD`；
  并发抢座由守卫条件 UPDATE 裁决，只放行一单（败者 409 `INVENTORY_CONFLICT`）。
- `GET /api/v1/orders/{orderId}/fulfillment`：订单履约单查询。
  订单未知或未进入履约（未支付）→ 404 `NOT_FOUND`；
  履约单停在 `PENDING_SELLER` 直到卖家交付 + 平台核验（见 `docs/architecture/trading-model.md`）。

### 观演人实名（一票一证）

订单行可携带 `attendees`（观演人姓名 + 证件号，V25 迁移），原型口径见
`docs/design/prototypes/web/purchase.html` 步骤 3：

- `POST /orders` 请求行为可选 `attendees: [{"name","idNumber"}]`；提供时数量必须等于行 `quantity`
  （一票一证，违反 → 400 `VALIDATION_FAILED`）；姓名与证件号必填、去空白后长度上限 64 / 32。
- **同一场次同一证件至多一张**：同订单内与跨订单都校验（按行 `sessionId` 判重），
  违反 → 409 `ORDER_CONFLICT`；未声明 `sessionId` 的行无从按场次判重，不参与该约束；
  并发提交由 `ux_order_attendees_session_idno` 唯一索引兜底，败者同 409。
- 读面（订单视图行内 `attendees`）证件号**脱敏**：留前 3 后 4，长度 ≤ 7 全打码；
  原文仅落库供实名校验。无实名的订单 `attendees` 为空数组（向后兼容）。
- 提交后不可修改（订单明细快照同一不可变口径）。
- 幂等键请求指纹包含 `attendees`：同键不同实名 → `IDEMPOTENCY_CONFLICT`。

支付与退款接口已暴露 HTTP：

- `POST /api/v1/orders/{orderId}/payments`：为订单发起支付，**金额与币种由服务端取订单应付快照**，
  请求体只携带渠道（`{"channel":"..."}`），客户端传多少金额都不参与计算；
  订单不存在 → 404 `ORDER_NOT_FOUND`，渠道不支持 → 409 `PAYMENT_CONFLICT`；成功返回 201。
- `GET /api/v1/orders/{orderId}/payments`：订单的支付历史（创建顺序），未支付返回空数组。
- `POST /api/v1/orders/{orderId}/refunds`：发起退款，请求体 `{"paymentNo","amount","reason"}`，
  以 `Idempotency-Key` 头判重（同键同请求返回原退款单）；缺失幂等键 → 400，
  支付单不属于该订单 → 404 `PAYMENT_NOT_FOUND`，支付未成功 → 409 `PAYMENT_CONFLICT`，
  超额退款 → 409 `REFUND_CONFLICT`；成功返回 201。

支付回调（验签、去重、UNKNOWN 待核实）属渠道到后端的通道，**不在这组客户端端点内**，
模块层语义见 `docs/architecture/payment-state-machine.md`。

### 订单支付倒计时

订单视图（`GET /api/v1/orders/{orderId}`、下单与取消响应）带 `expiresAt`（支付截止时刻）、
`payExpiresInSeconds`（剩余支付秒数，已过期不再为负）与 `serverTime`（响应生成时的服务端时间）。

倒计时由客户端以 `serverTime` 为锚点推算，**不得用本机时钟判断是否超时**：是否超时只认后端结论
（`POST /orders/{orderId}/cancel` 与 Worker 超时任务后的 `status`）。非 `PENDING_PAYMENT` 状态
的 `payExpiresInSeconds` 恒为 `0`——已取消 / 已超时 / 已支付的订单没有可支付的剩余时间。

### 支付失败重试与换渠道

支付单视图带 `retryable`：本单处于 `FAILED`、且所属订单仍是待支付且未过支付截止期时为 `true`。
客户端据此显示「可重新支付 / 可换渠道」，**不据此推断支付结果**（支付成功只认渠道回调）。

- `GET /api/v1/payments/channels`：可用渠道编码列表（由已注册网关决定）。渠道编码不硬编码在各端，
  换渠道下单前先读本接口。
- `POST /api/v1/payments/{paymentNo}/retry`：失败单重试，请求体 `{"channel":"..."}`，
  `channel` 省略时沿用原支付单的渠道。**金额与币种仍取订单应付快照**，客户端传值不参与计算。
  成功返回 201 与新支付单；原支付单保持 `FAILED`，历史不改写。
- 守卫：未知支付单 → 404 `PAYMENT_NOT_FOUND`；源单不是失败态（含进行中与成功态）→ 409
  `PAYMENT_CONFLICT`；订单非 `PENDING_PAYMENT` 或已过支付截止期 → 409 `ORDER_CONFLICT`；
  渠道不支持 → 409 `PAYMENT_CONFLICT`。

### 排队 / 候补（售罄后等待补票）

候补用于场次售罄后的等待队列：库存释放后按加入顺序递补，为队首原子预留并生成待支付订单；
未在支付截止期内支付则该席位过期，库存回到池中、顺延下一位。

- `POST /api/v1/waitlist`：加入候补，请求体
  `{"sessionId","poolCode","productId","productName","quantity","unitPrice","currency","userId"}`，
  成功返回 201 与候补条目（`waitlistNo` 为对外稳定标识）。
- `GET /api/v1/waitlist/{waitlistNo}`：候补条目。`position` 为同池 `WAITING` 条目中的位次（从 1 起），
  非 `WAITING` 状态为 `null`；`orderNo` 与 `payExpiresInSeconds` 仅递补后有值。
- `POST /api/v1/waitlist/{waitlistNo}/cancel`：退出排队。未知条目 → 404 `WAITLIST_NOT_FOUND`；
  重复取消幂等返回 `false`（`canceled: false`）；已递补或已终态 → 409 `WAITLIST_CONFLICT`
  （已递补的席位走订单取消 `POST /orders/{orderId}/cancel`）。
- `GET /api/v1/waitlist?userId=`：某用户的候补列表（加入时间倒序）；缺 `userId` → 400
  `VALIDATION_FAILED`。

候补状态机：

| 状态 | 含义 | 下一状态 |
|------|------|----------|
| WAITING | 排队中 | PROMOTED（递补成功）/ CANCELLED（主动退出） |
| PROMOTED | 已递补：已原子预留并生成待支付订单 | CLAIMED（订单已支付）/ EXPIRED（订单超时或取消） |
| CLAIMED | 席位已成单（订单已支付） | 终态 |
| EXPIRED | 递补后未在支付截止期内支付，席位过期 | 终态 |
| CANCELLED | 主动退出排队 | 终态 |

递补由 Worker 任务执行（`awn.worker.tasks.waitlist-promotion`，默认关闭），按池 FIFO：队首预留
成功才生成订单，预留失败（库存仍不足）保持排队等待下一轮；单条失败不影响其余条目。

候补递补生成的订单与普通下单同源（同一库存预留与金额计算口径），金额按候补条目快照的
`unitPrice` 重算。商品改价不回溯历史候补条目。

### 订单争议（受理与裁决）

买家对已支付订单发起退款 / 重交付诉求，平台裁决。设计口径见 `docs/architecture/dispute-handling.md`：
**裁决只记结论不动钱**（退款执行走既有退款链路由运营另行发起），受理与裁决都不改订单状态机。

- `POST /api/v1/orders/{orderNo}/disputes`：开争议，体
  `{"userId","reason","description","evidenceUrl","requestedResolution","requestedAmount"}`，
  成功 201 + `Location: /api/v1/disputes/{disputeNo}`。`reason` ∈ `NOT_DELIVERED / TICKET_INVALID /
  DESCRIPTION_MISMATCH / OTHER`，`requestedResolution` ∈ `REFUND / REDELIVER`（未知枚举 400）。
  `REFUND` 必填 `requestedAmount` 且 ∈ (0, 订单应付]；`REDELIVER` 不得带金额；`description` 必填；
  `evidenceUrl` 可选但限 `http(s)://` 白名单（非法 400）。守卫失败一律 409 `DISPUTE_CONFLICT`：
  非下单人、订单状态不在 `PAID / FULFILLING / FULFILLED`、同单已有在途争议（`OPEN` / `UNDER_REVIEW`）。
- `GET /api/v1/orders/{orderNo}/disputes?userId=`：该用户在该订单上的争议列表；缺 `userId` → 400。
- `POST /api/v1/disputes/{disputeNo}/withdraw`：受理人撤回（体 `{"userId"}`），`OPEN` / `UNDER_REVIEW`
  → `WITHDRAWN`。争议单不存在 → 404 `DISPUTE_NOT_FOUND`；非受理人 → 409 `DISPUTE_CONFLICT`。

卖家随争议落库快照：取订单首个凭证型订单行的挂牌 `sellerId` / `listingNo` / `unitNo`；
数量型与座位型订单行无卖家，字段为 `null`。

状态机：`OPEN → UNDER_REVIEW → RESOLVED`，`OPEN` / `UNDER_REVIEW` 可 `WITHDRAWN`（终态）。
裁决权限仅运营端（`X-Admin-Token`，见「运营接口」）；`resolution` ∈
`REFUND_APPROVED / REDELIVER_APPROVED / REJECTED`，裁决人记 `decidedBy` / `decidedAt`。

### 卖家面（挂牌与争议）

交易面当前无认证中间件：卖家以请求体或路径中的 `sellerNo` 声明身份，服务端校验声明者与
资源归属一致（未知主体一律 404）。卖家与买家一样**没有受理或裁决入口**，裁决只在运营接口。
设计口径见 `docs/architecture/dispute-handling.md`。

- `POST /api/v1/listings/{listingNo}/delist`：取消挂牌，体 `{"sellerNo"}`，返回挂牌视图。
  挂牌未知 → 404 `NOT_FOUND`；卖家未知 → 404 `NOT_FOUND`；非归属卖家 → 409 `CONFLICT`；
  挂牌已 `RESERVED` / `SOLD` / `DELISTED` → 409 `CONFLICT`。
- `GET /api/v1/sellers/{sellerNo}/disputes`：该卖家挂牌涉及的争议列表（按争议开单时快照的
  挂牌卖家匹配，创建倒序限 50 条）；卖家未知 → 404 `NOT_FOUND`。
- `POST /api/v1/disputes/{disputeNo}/seller-response`：提交申辩，体 `{"sellerNo","content"}`，
  成功 201 与申辩视图 `{"disputeNo","sellerNo","content","createdAt"}`；申辩追加留痕不覆盖。
  守卫：争议未知 → 404 `DISPUTE_NOT_FOUND`；`content` 空 → 400 `VALIDATION_FAILED`；
  卖家未知 → 404 `NOT_FOUND`；争议无卖家快照（纯平台库存订单）/ 声明卖家与快照不符 /
  争议已终态（`RESOLVED` / `WITHDRAWN`）→ 409 `DISPUTE_CONFLICT`。

申辩只在运营端读取（见「运营接口」），不进买家面订单争议列表响应。

### 幂等键规则

订单创建与退款在模块层强制幂等键（缺失即拒绝）：

- 相同幂等键重复提交相同请求，返回原业务结果；
- 相同幂等键对应不同请求内容，返回明确的冲突错误。

HTTP 端点 `POST /api/v1/orders`（订单）与 `POST /api/v1/orders/{orderId}/refunds`（退款）
以 `Idempotency-Key` 请求头传入幂等键，缺失返回 400 `VALIDATION_FAILED`；
订单创建成功返回 201 与 `Location: /api/v1/orders/{orderNo}`。

## 运营接口

```text
POST /api/v1/campaigns
POST /api/v1/campaigns/{campaignCode}/draft
POST /api/v1/campaigns/{campaignCode}/validate
POST /api/v1/campaigns/{campaignCode}/publish
POST /api/v1/campaigns/{campaignCode}/unpublish

POST   /api/v1/campaigns/{campaignCode}/rules
DELETE /api/v1/campaigns/{campaignCode}/rules/{ruleId}
POST   /api/v1/campaigns/{campaignCode}/faq
DELETE /api/v1/campaigns/{campaignCode}/faq/{faqId}
POST   /api/v1/campaigns/{campaignCode}/assets
DELETE /api/v1/campaigns/{campaignCode}/assets/{assetId}

POST /api/v1/admin/inventory/adjustments

GET  /api/v1/admin/orders
GET  /api/v1/admin/inventory
GET  /api/v1/admin/reconciliation
GET  /api/v1/admin/anomalies
POST /api/v1/admin/anomalies/{alertId}/acknowledge
GET  /api/v1/admin/audit-logs

POST /api/v1/admin/events/{eventCode}/publish

GET    /api/v1/admin/venues
GET    /api/v1/admin/events/{eventCode}/sessions
POST   /api/v1/admin/sessions/{sessionCode}/venue
DELETE /api/v1/admin/sessions/{sessionCode}/venue

POST   /api/v1/admin/sessions/{sessionCode}/seats
GET    /api/v1/admin/sessions/{sessionCode}/seats

GET  /api/v1/admin/disputes
GET  /api/v1/admin/disputes/{disputeNo}/seller-responses
POST /api/v1/admin/disputes/{disputeNo}/accept
POST /api/v1/admin/disputes/{disputeNo}/adjudicate

POST   /api/v1/admin/stars
PUT    /api/v1/admin/stars/{personId}
POST   /api/v1/admin/stars/{personId}/publish
POST   /api/v1/admin/stars/{personId}/unpublish
DELETE /api/v1/admin/stars/{personId}
POST   /api/v1/admin/works
PUT    /api/v1/admin/works/{workId}
POST   /api/v1/admin/works/{workId}/publish
POST   /api/v1/admin/works/{workId}/unpublish
DELETE /api/v1/admin/works/{workId}
POST   /api/v1/admin/works/{workId}/cast
```

专场管理写操作与运营接口同权（`X-Admin-Token`），公开读 GET 放行。

专场内容配置（须知 / 常见问题 / 素材）同权：`POST /campaigns/{code}/rules`（体 `{"title","content"}`）、
`POST /campaigns/{code}/faq`（体 `{"question","answer"}`）、`POST /campaigns/{code}/assets`
（体 `{"assetType","url","altText","displayOrder"}`，`assetType` 取 `CampaignAsset` 白名单值），
成功返回 201 与条目视图；`DELETE` 对应条目，成功返回 200 空体。删除按**归属专场**匹配
（`DELETE /campaigns/{code}/assets/{assetId}` 只删本专场条目），他专场条目不受理；
专场未知 → 404 `CAMPAIGN_NOT_FOUND`，素材类型不在白名单 → 400 `VALIDATION_FAILED`。

图鉴运营写操作同权（`X-Admin-Token`）：建人物 / 作品草稿、发布（`DRAFT` → `PUBLISHED` 守卫，重复发布 → 409 `CONFLICT`）、给作品绑定人物身份（`roleType` / `roleName` / `billingOrder`，重复绑定 → 409）。绑定与发布校验未知资源 → 404 `PERSON_NOT_FOUND` / `WORK_NOT_FOUND`。

场次场馆配置（巡回赛逐场换城市）经 `X-Admin-Token`：`GET /admin/venues` 列全部场馆；`GET /admin/events/{eventCode}/sessions` 列活动下全状态场次（响应 `geo` 为解析结果：场次场馆覆盖 → 父活动场馆 → `null`）；`POST /admin/sessions/{sessionCode}/venue`（体 `{"venueCode":...}`）挂场馆、`DELETE` 摘场馆回退继承，响应为操作后的解析 geo（无场馆时空体）。`PUT /admin/venues/{venueCode}`（体：`name`/`city`/`address`/`latitude`/`longitude`/`mapImageUrl`/`mapMarkerX`/`mapMarkerY`）为场馆配置**全量覆盖**——请求体里缺省即清空该字段，坐标与图上标记各自成对守卫（半截一律 400 `VALIDATION_FAILED`），`mapImageUrl` 域白名单校验（拒绝 → 400）。未知场次 / 场馆 → 404 `SESSION_NOT_FOUND` / `VENUE_NOT_FOUND`。

铺座（座位运营入口）经 `X-Admin-Token`：`POST /admin/sessions/{sessionCode}/seats`（体
`{"productCode","positions":[{"section","row","seat"}]}`）一次铺一个商品在同一场次下的座位，
成功 201 与座位视图列表；**批次整体成功或整体失败**——商品编码经 catalog 校验（未知 → 404
`NOT_FOUND`）、位置字段必填、同场次同位置重复 → 400 `VALIDATION_FAILED`。`GET
/admin/sessions/{sessionCode}/seats` 列该场次座位（运营视图含占座订单号 `orderNo`，公开座位图
剥去该字段）；场次编码不存在时返回空列表（字符串松耦合，不校验场次存在性）。

争议裁决经 `X-Admin-Token`：`GET /admin/disputes` 列争议单（受理人 / 卖家快照 / 状态 / 裁决）；
`POST /admin/disputes/{disputeNo}/accept` 受理（`OPEN` → `UNDER_REVIEW`）；`POST
/admin/disputes/{disputeNo}/adjudicate`（体 `{"actor","resolution","note"}`，`resolution` ∈
`REFUND_APPROVED / REDELIVER_APPROVED / REJECTED`）裁决（`UNDER_REVIEW` → `RESOLVED`）。争议单
不存在 → 404 `DISPUTE_NOT_FOUND`；非法迁移（重复受理 / 重复裁决 / 非在途撤回）→ 409
`DISPUTE_CONFLICT`。`GET /admin/disputes/{disputeNo}/seller-responses` 列卖家申辩
（提交时间正序，裁决依据），争议未知同样 404。买家与卖家侧都不得裁决。

图鉴草稿可编辑（`PUT`，校验口径同创建）与删除（`DELETE`，关系行随外键级联清除）；已发布内容冻结，改动需先 `unpublish`（`PUBLISHED` → `DRAFT`，未发布再撤 → 409），否则 `PUT` / `DELETE` 一律 409 `CONFLICT`。删除 `DELETE` 成功返回 200 空体。

库存调整必须通过专门接口，并要求**权限校验、操作原因、变更流水和审计记录**。

## 多端接入约定

| 端 | 接入方式 |
|--------|--------|
| 网页端 `apps/web` | Next.js 服务端 + `packages/api-client` |
| 小程序 `apps/miniprogram` | Taro 请求封装 + 自研 section 子集渲染（运行环境不含 DOM，未依赖 `packages/campaign-renderer`） |
| 客户端 `apps/client` | Flutter + Dio，模型按本文档契约用 json_serializable 生成（只读活动/场次/商品链路） |
| 运营后台 `apps/admin` | `packages/api-client`（管理接口作用域） |

支付相关接口仅由后端与支付渠道交互，任何端**不得**仅凭前端参数确认支付成功。
