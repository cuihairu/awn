# ADR-0002：PostgreSQL 是交易库存的唯一权威，预留使用原子条件更新

- 状态：已接受
- 日期：2026-10-11

## 背景

票务场景超卖是致命缺陷。缓存、前端展示值、专场配置都不能作为扣减依据。

## 决策

- PostgreSQL 是交易库存的**最终权威来源**。
- 库存预留禁止"先查再扣"，必须使用条件更新：

```sql
UPDATE inventory_balance
SET reserved_quantity = reserved_quantity + :quantity,
    version = version + 1
WHERE inventory_pool_id = :poolId
  AND total_quantity - reserved_quantity - sold_quantity >= :quantity;
```

影响行数为 0 即预留失败（`updated_at` 由触发器维护，不在语句中更新）。

- 该 SQL 与订单、预留记录、Outbox 事件在**同一数据库事务**中提交。
- 数据库非负约束与库存上限约束作为最后一道防线。
- 独立票品（座位/二维码/票号）使用 `ticket_units` 记录唯一状态。
- 只有 `inventory` 模块有权执行预留、确认、释放；其他模块只能调用其 `api` 接口。

## 后果

- 正面：并发正确性由数据库保证，可被真实 PostgreSQL 并发集成测试验证。
- 负面：热点库存行存在行锁竞争，需要后续评估单行热点优化（如库存分片），但**不得**以牺牲正确性为代价。
