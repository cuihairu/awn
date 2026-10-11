# 库存一致性（防超卖）

这是 AWN 开发中**最不能交给 Code Agent 自行发挥**的部分。

## 原子预留

不能使用"先查库存，再扣库存"的普通流程。必须在 PostgreSQL 中使用条件更新：

```sql
UPDATE inventory_balances
SET reserved_quantity = reserved_quantity + :quantity,
    version = version + 1,
    updated_at = NOW()
WHERE inventory_pool_id = :poolId
  AND total_quantity - reserved_quantity - sold_quantity >= :quantity
RETURNING inventory_pool_id;
```

没有返回记录，预留就失败。

这条 SQL 必须放在真实的数据库事务中，并与订单、预留记录和 Outbox 事件一起提交。

## 不变量

```text
available = total - reserved - sold
total >= 0
reserved + sold <= total
```

数据库通过非负约束和库存上限约束提供最后一道防线，应用层的条件更新是第一道防线，两者缺一不可。

## 订单创建事务

```mermaid
sequenceDiagram
    participant U as 用户
    participant O as Order
    participant I as Inventory
    participant DB as PostgreSQL

    U->>O: 创建订单 + 幂等键
    O->>DB: 开启事务
    O->>DB: 校验商品、规则、价格
    O->>I: 原子预留库存
    I->>DB: 条件 UPDATE
    DB-->>I: 成功 / 库存不足
    I-->>O: 预留结果

    alt 预留成功
        O->>DB: 写订单与明细
        O->>DB: 写预留记录与 Outbox
        O->>DB: COMMIT
        O-->>U: 返回待支付订单
    else 预留失败
        O->>DB: ROLLBACK
        O-->>U: 返回库存不足
    end
```

订单和库存预留必须处于**同一个本地事务**中。外部支付请求不能放进数据库事务中长时间等待。

## 库存预留状态

```text
ACTIVE
  ├── CONFIRMED：支付确认并转为已售
  └── RELEASED：合法取消或超时释放
```

### 释放

释放库存时，必须在**同一个事务**中执行：

1. 条件更新预留状态，仅允许 `ACTIVE → RELEASED`。
2. 只有本次状态转换成功时，才减少预留库存。
3. 写入库存流水和必要的业务事件。
4. 提交事务。

重复执行释放任务不会重复加回库存。

### 确认

支付确认必须在同一个事务中完成 `ACTIVE → CONFIRMED`，并同时将预留数量转为已售数量。

## 支付成功与超时释放发生竞态怎么办？

必须由**数据库事务和状态机**共同解决，不能让定时任务与支付回调各自随意更新状态。

特别是支付渠道返回超时、但实际上可能已经扣款的情况：

- 订单进入**支付结果待核实**状态。
- Worker 查询支付渠道并进行对账。
- 未明确确认支付失败前，不得简单释放库存。
- 如果库存已经合法释放，但后来确认支付成功，**不能无条件把订单标记为正常履约成功**。
- 应进入异常订单处理流程，根据供货情况、支付事实和平台规则执行补救或退款。

::: warning 总结
超卖防护需要**数据库约束、原子 SQL、事务、幂等状态转换、支付对账和故障恢复**共同完成，任何单一手段都不足够。
:::
