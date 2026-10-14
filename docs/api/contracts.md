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
| CAMPAIGN_NOT_FOUND | 404 | 专场不存在 |
| PAYMENT_NOT_FOUND | 404 | 支付单不存在 |
| REFUND_NOT_FOUND | 404 | 退款单不存在 |
| PERSON_NOT_FOUND | 404 | 人物不存在或未发布（明星图鉴） |
| WORK_NOT_FOUND | 404 | 作品不存在或未发布（明星图鉴） |
| ORDER_CONFLICT | 409 | 订单状态机冲突（非法迁移） |
| IDEMPOTENCY_CONFLICT | 409 | 幂等键对应了不同的请求内容 |
| INSUFFICIENT_INVENTORY | 409 | 库存不足 |
| INVENTORY_CONFLICT | 409 | 预留状态机冲突（如确认已释放的预留） |
| PAYMENT_CONFLICT | 409 | 支付状态机冲突（如重复确认已成功的支付） |
| REFUND_CONFLICT | 409 | 退款状态机冲突或超退 |
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
GET /api/v1/campaigns/{campaignCode}
GET /api/v1/campaigns/{campaignCode}/published
GET /api/v1/campaigns/{campaignCode}/render

POST /api/v1/orders
GET  /api/v1/orders/{orderId}
POST /api/v1/orders/{orderId}/cancel

POST /api/v1/orders/{orderId}/payments
GET  /api/v1/orders/{orderId}/payments
POST /api/v1/orders/{orderId}/refunds
GET  /api/v1/orders/{orderId}/fulfillment

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
- `GET /api/v1/intelligence/events/{eventId}/prices` 返回该活动下**所有场次**的价格历史（按观测时间倒序，每条可溯源）；活动未知或未公开 → 404；场次无价格数据时返回空数组。

活动发布（`DRAFT` → `PUBLISHED`）是运营动作，经 `X-Admin-Token` 鉴权暴露：`POST /api/v1/admin/events/{eventCode}/publish`（见「运营接口」）；仅草稿可发布，重复发布 → 409 `CONFLICT`，未知活动 → 404 `EVENT_NOT_FOUND`。

公共商品接口的库存摘要为展示口径（当前直查数据库，未接缓存），**下单时必须重新验证**。商品详情、可售摘要与场次商品列表均透出 `poolCode`（未建池为 `null`，按未开售处理）：`POST /orders` 数量行需要它，未建池商品无法拼出合法订单行。

## 交易接口

以下接口已暴露 HTTP：

- `POST /api/v1/checkout/quote`：结算报价。请求体与创建订单同构（不含幂等键与用户），
  按订单行同一服务端口径计算金额；**报价不扣减任何库存、不产生订单**，
  下单时金额重新计算并逐行原子预留。非法行（负行合计 / 双来源 / 非正数量）→ 400 `VALIDATION_FAILED`。
- `GET /api/v1/orders/{orderId}/fulfillment`：订单履约单查询。
  订单未知或未进入履约（未支付）→ 404 `NOT_FOUND`；
  履约单停在 `PENDING_SELLER` 直到卖家交付 + 平台核验（见 `docs/architecture/trading-model.md`）。

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

POST /api/v1/admin/inventory/adjustments

GET  /api/v1/admin/orders
GET  /api/v1/admin/inventory
GET  /api/v1/admin/reconciliation
GET  /api/v1/admin/anomalies
POST /api/v1/admin/anomalies/{alertId}/acknowledge
GET  /api/v1/admin/audit-logs

POST /api/v1/admin/events/{eventCode}/publish

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

图鉴运营写操作同权（`X-Admin-Token`）：建人物 / 作品草稿、发布（`DRAFT` → `PUBLISHED` 守卫，重复发布 → 409 `CONFLICT`）、给作品绑定人物身份（`roleType` / `roleName` / `billingOrder`，重复绑定 → 409）。绑定与发布校验未知资源 → 404 `PERSON_NOT_FOUND` / `WORK_NOT_FOUND`。

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
