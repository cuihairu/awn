# 订单与支付状态机

订单、支付、履约三套状态**相互独立**，禁止混用。状态转换必须通过领域服务校验，不允许任意 API 直接修改状态字符串。

## 订单状态机

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT
    PENDING_PAYMENT --> PAID: 确认支付成功
    PENDING_PAYMENT --> CANCELLED: 合法取消
    PENDING_PAYMENT --> EXPIRED: 确认未支付且预留释放

    PAID --> FULFILLING: 开始履约
    PAID --> REFUND_PENDING: 允许退款

    FULFILLING --> FULFILLED: 交付完成
    FULFILLING --> REFUND_PENDING: 允许退款或履约失败

    FULFILLED --> REFUND_PENDING: 适用退款规则

    REFUND_PENDING --> REFUNDED: 退款成功
    REFUND_PENDING --> PAID: 退款拒绝或取消

    CANCELLED --> [*]
    EXPIRED --> [*]
    REFUNDED --> [*]
```

## 支付状态

独立于订单状态管理：

```text
CREATED
PENDING
SUCCEEDED
FAILED
UNKNOWN
REFUNDING
PARTIALLY_REFUNDED
REFUNDED
```

## 履约状态

同样独立管理：

```text
NOT_STARTED
PENDING_SELLER
PENDING_VERIFICATION
IN_PROGRESS
COMPLETED
FAILED
CANCELLED
```

## 支付回调幂等

支付模块必须实现：

- 回调签名验证。
- 商户订单号、金额、币种和支付渠道校验。
- 支付渠道交易号唯一约束。
- 重复回调去重。
- 支付成功状态的条件更新。
- 支付事件持久化。
- 支付渠道查询与定期对账。
- 异常状态告警。

**支付成功回调即使到达多次，也只能产生一次有效的业务确认。**

## 退款幂等

退款模块必须有自己的退款编号、渠道退款编号、退款金额、状态和幂等键。

要确保：

- 重复退款请求不会重复发起资金退款。
- 重复退款回调不会重复更新订单或履约。
- 成功退款总金额不能超过可退款金额。
- 部分退款有明确的累计金额校验。
- 退款渠道结果未知时先查询，不盲目再次退款。

## 竞态与异常订单

支付成功与库存释放发生竞争时（例如支付渠道返回超时、但实际上可能已经扣款）：

1. 订单进入支付结果待核实状态。
2. Worker 查询支付渠道并对账。
3. 未明确确认支付失败前，不释放库存。
4. 若库存已合法释放后确认支付成功，订单进入**异常订单处理流程**，依据供货情况、支付事实与平台规则执行补救或退款，不能盲目标记履约成功。
