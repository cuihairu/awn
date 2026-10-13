# 领域模型

AWN 的领域模型围绕一条主链展开：**活动 → 场次 → 商品 → 库存池 → 统一结算 → 统一订单 → 统一支付**。专场只是商品的组织与展示层，不参与交易语义。

四类业务事实（平台自营票量 / 第三方挂牌 / 独立票品 / 外部情报报价）的数据模型落点、
marketplace 供货挂牌链路与全链事务边界，见 [交易全链模型](./trading-model)。

## 业务关系

```mermaid
flowchart TD
    EVENT["活动 Event"] --> SESSION["场次 Session"]
    SESSION --> PRODUCT["交易商品 Product"]
    PRODUCT --> POOL["库存池 Inventory Pool"]
    POOL --> BALANCE["唯一权威库存余额"]

    CAMPAIGN_A["演唱会专场"] --> REF_A["商品引用"]
    CAMPAIGN_B["F1 专场"] --> REF_B["商品引用"]
    CAMPAIGN_C["音乐节专场"] --> REF_C["商品引用"]

    REF_A --> PRODUCT
    REF_B --> PRODUCT
    REF_C --> PRODUCT

    PRODUCT --> CHECKOUT["统一结算"]
    CHECKOUT --> ORDER["统一订单"]
    ORDER --> PAYMENT["统一支付"]
```

同一个商品可以出现在多个专场里，但只对应**同一个库存池**或明确关联的库存分配规则。

专场不能把商品复制一份，再给复制品分配虚构库存。

## 核心数据表

推荐 PostgreSQL 为唯一交易权威数据库。初期使用一个数据库实例，按领域使用清晰的表命名或 Schema。高风险交易逻辑尽量放在同一数据库事务内。

| 领域 | 表（与 Flyway 迁移一致） |
|--------|--------|
| 活动 / 场馆 | `venue`、`event`、`session`（V4） |
| 商品 / 票档 | `ticket_category`、`product`（V4） |
| 库存 | `inventory_pool`、`inventory_balance`、`inventory_reservation`、`inventory_transaction`（V2） |
| 专场 | `campaigns`、`campaign_revisions`、`campaign_events`、`campaign_products`、`campaign_assets`（V5） |
| 订单 | `orders`、`order_items`（V3；幂等键内联于 `orders.idempotency_key`） |
| 支付 / 退款 | `payments`、`refunds`（V6；退款幂等键内联于 `refunds.idempotency_key`） |
| 履约 | `fulfillments`、`delivery_records`（V7） |
| marketplace | `sellers`、`supply_reviews`、`listings`、`ticket_units`（V7） |
| 事件与收件 | `outbox_messages`（V3）、`inbox_messages`（V6） |
| 审计与告警 | `audit_logs`、`alerts`（V8） |
| 情报 | `intelligence.data_sources`、`intelligence.raw_observations`、`intelligence.normalized_observations`、`intelligence.price_history`（V8，独立 schema） |
| 通知 | 暂无表（notification 模块当前仅适配外部通道） |

## 库存余额模型

数量型库存至少包含：

```text
inventory_balance
  inventory_pool_id
  total_quantity
  reserved_quantity
  sold_quantity
  version
  updated_at
```

核心不变量：

```text
available = total - reserved - sold
```

必须保证：

```text
total >= 0
reserved + sold <= total
```

数据库应通过非负约束和库存上限约束提供最后一道防线。

对于有独立座位、独立二维码或独立票品编号的商品，需要逐票的唯一状态记录，不能仅依靠数量余额。
注意当前实现中 `ticket_units` 是**marketplace 挂牌的 1:1 票品实体**（外键挂在 `listings` 上）；
平台自营座位型票品已独立建模为 `platform_seats`（V17，挂在场次与商品编码上），不复用该表——详见 [交易全链模型](./trading-model)。

## 订单快照

`order_items` 至少需要保存：

```text
order_id
product_id
product_name_snapshot
session_id
ticket_category_snapshot
quantity
unit_price
discount_amount
fee_amount
line_total
```

下单时计算并保存实际成交金额、币种、商品规则版本和必要的供货信息。

**金额使用最小货币单位整数或精确 `NUMERIC` 类型，禁止用浮点数计算实际交易金额。**

## 状态定义

订单状态不能兼任支付状态和履约状态，三者独立管理：

- 订单状态：`PENDING_PAYMENT / PAID / FULFILLING / FULFILLED / REFUND_PENDING / REFUNDED / CANCELLED / EXPIRED`
- 支付状态：`CREATED / PENDING / SUCCEEDED / FAILED / UNKNOWN / REFUNDING / PARTIALLY_REFUNDED / REFUNDED`
- 履约状态：`NOT_STARTED / PENDING_SELLER / PENDING_VERIFICATION / IN_PROGRESS / COMPLETED / FAILED / CANCELLED`

详见 [订单与支付状态机](./payment-state-machine)。
