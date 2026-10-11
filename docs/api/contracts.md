# API 契约

所有业务接口统一使用 `/api/v1`。所有接口在开发阶段形成 OpenAPI 契约，前后端根据同一契约开发，避免各端分别猜测字段和响应格式。

## 公共接口

```text
GET /api/v1/events
GET /api/v1/events/{eventId}
GET /api/v1/sessions/{sessionId}
GET /api/v1/products/{productId}
GET /api/v1/products/{productId}/availability
GET /api/v1/campaigns/{slug}
GET /api/v1/intelligence/events/{eventId}/prices
```

公共商品接口可以展示缓存库存摘要，但**下单时必须重新验证**。

## 交易接口

```text
POST /api/v1/checkout/quote
POST /api/v1/orders
GET  /api/v1/orders/{orderId}
POST /api/v1/orders/{orderId}/cancel

POST /api/v1/orders/{orderId}/payments
GET  /api/v1/orders/{orderId}/payments

POST /api/v1/orders/{orderId}/refunds
GET  /api/v1/orders/{orderId}/fulfillment
```

### 幂等键规则

订单创建要求 `Idempotency-Key` 请求头：

- 同一用户使用**相同幂等键重复提交相同请求**，返回原业务结果。
- 相同幂等键对应**不同请求内容**时，返回明确的冲突错误。

## 运营接口

```text
POST  /api/v1/admin/events
PATCH /api/v1/admin/events/{eventId}

POST  /api/v1/admin/campaigns
PATCH /api/v1/admin/campaigns/{campaignId}/draft
POST  /api/v1/admin/campaigns/{campaignId}/validate
POST  /api/v1/admin/campaigns/{campaignId}/publish
POST  /api/v1/admin/campaigns/{campaignId}/unpublish

POST  /api/v1/admin/products
POST  /api/v1/admin/inventory/adjustments

GET   /api/v1/admin/orders
GET   /api/v1/admin/reconciliation
GET   /api/v1/admin/audit-logs
```

库存调整必须通过专门接口，并要求**权限校验、操作原因、变更流水和审计记录**。

## 多端接入约定

| 端 | 接入方式 |
|--------|--------|
| 网页端 `apps/web` | Next.js 服务端 + `packages/api-client` |
| 小程序 `apps/miniprogram` | Taro 请求封装 + `packages/api-client` 类型 |
| 客户端 `apps/client` | Flutter Dio 客户端，按 OpenAPI 生成模型 |
| 运营后台 `apps/admin` | `packages/api-client`（管理接口作用域） |

支付相关接口仅由后端与支付渠道交互，任何端**不得**仅凭前端参数确认支付成功。
