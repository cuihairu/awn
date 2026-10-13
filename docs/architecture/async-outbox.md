# 异步与 Outbox

初期**无需**为了异步任务先引入 Kafka 或 RabbitMQ。推荐用 PostgreSQL Outbox 加独立 Worker 进程，先实现可靠、可重试的后台任务。

## Outbox 事件

订单创建、支付确认等关键事务，在提交业务数据时**同步写入 Outbox**（同一数据库事务）：

```text
outbox_messages
  id
  aggregate_type
  aggregate_id
  event_type
  payload
  status
  attempts
  next_attempt_at
  created_at
  processed_at
```

## Inbox 消费去重

同一事件可能对同一消费者重复投递，消费侧以 `inbox_messages` 表做幂等：

```text
inbox_messages
  id
  consumer          消费者标识
  message_id        指向 outbox_messages(id)
  event_type
  processed_at
  created_at
  UNIQUE (consumer, message_id)
```

处理器在执行副作用前插入本表，唯一键冲突即视为已消费并跳过。

## Worker 职责

Worker 进程与 API 共享同一代码包，只启用后台任务：

- 处理订单超时。
- 扫描并释放已到期的库存预留（带 TTL 的预留过期后回补库存；幂等由预留状态机守卫保证）。
- 执行库存、支付和退款对账。
- 分发并重试 Outbox 事件，按处理器路由到履约等下游。
- 告警并处理超过重试上限的事件（转入 FAILED 并告警，无独立死信存储）。

未实现：邮件 / 短信通道接入（`notification` 模块已落地站内信：`ORDER_CREATED` / `ORDER_PAID` 事件经 Outbox 投递，通道失败记 FAILED 不回滚订单；`EMAIL` / `SMS` 通道为显式未接入占位。`awn.worker.tasks.notification-dispatch` 仍为无任务认领的死配置）。

多个 Worker 实例竞争处理任务时，需要通过**行锁、条件更新或明确的任务租约**保证不会产生重复业务副作用。

## 与 Spring Modulith 的关系

Spring Modulith 的事件发布注册表可以帮助记录事务事件的投递状态和失败恢复；如果采用该能力，需要明确选择与 Flyway 兼容的持久化配置，并避免与自建 Outbox 重复承担同一种责任。

::: tip 建议
**交易关键事件先使用明确的数据库 Outbox；Spring Modulith 负责模块边界、模块事件和架构验证。** 不要在第一版同时实现两套重复的可靠投递机制。
:::
