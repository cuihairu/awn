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
```

以下接口目前只有模块层 facade（`EventFacade` 等），**未暴露 HTTP**，由后续阶段补齐控制器：

```text
GET /api/v1/events
GET /api/v1/events/{eventId}
GET /api/v1/sessions/{sessionId}
GET /api/v1/intelligence/events/{eventId}/prices
```

公共商品接口的库存摘要为展示口径（当前直查数据库，未接缓存），**下单时必须重新验证**。

## 交易接口

以下接口目前只有模块层实现（`PaymentFacade` / `FulfillmentFacade`），**未暴露 HTTP**，端点落地后本节方可验收：

```text
POST /api/v1/checkout/quote

POST /api/v1/orders/{orderId}/payments
GET  /api/v1/orders/{orderId}/payments

POST /api/v1/orders/{orderId}/refunds
GET  /api/v1/orders/{orderId}/fulfillment
```

### 幂等键规则

订单创建在模块层强制幂等键（`CreateOrderCommand.idempotencyKey`，缺失即拒绝）：

- 相同幂等键重复提交相同请求，返回原业务结果；
- 相同幂等键对应不同请求内容，返回明确的冲突错误（`IDEMPOTENCY_CONFLICT`）。

HTTP 端点 `POST /api/v1/orders` 以 `Idempotency-Key` 请求头传入幂等键，
缺失返回 400 `VALIDATION_FAILED`；创建成功返回 201 与 `Location: /api/v1/orders/{orderNo}`。

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
```

专场管理写操作与运营接口同权（`X-Admin-Token`），公开读 GET 放行。

库存调整必须通过专门接口，并要求**权限校验、操作原因、变更流水和审计记录**。

## 多端接入约定

| 端 | 接入方式 |
|--------|--------|
| 网页端 `apps/web` | Next.js 服务端 + `packages/api-client` |
| 小程序 `apps/miniprogram` | Taro 请求封装（当前未依赖 `packages/api-client`，待接入） |
| 客户端 `apps/client` | Flutter 工程骨架（仅 `lib/main.dart`，Dio 客户端与生成模型待建） |
| 运营后台 `apps/admin` | `packages/api-client`（管理接口作用域） |

支付相关接口仅由后端与支付渠道交互，任何端**不得**仅凭前端参数确认支付成功。
