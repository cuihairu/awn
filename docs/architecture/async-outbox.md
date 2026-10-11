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

## Worker 职责

Worker 进程与 API 共享同一代码包，只启用后台任务：

- 扫描到期库存预留。
- 处理订单超时。
- 发送通知。
- 启动履约任务。
- 执行支付和退款对账。
- 重试失败事件。
- 告警并处理超过重试上限的事件。

多个 Worker 实例竞争处理任务时，需要通过**行锁、条件更新或明确的任务租约**保证不会产生重复业务副作用。

## 与 Spring Modulith 的关系

Spring Modulith 的事件发布注册表可以帮助记录事务事件的投递状态和失败恢复；如果采用该能力，需要明确选择与 Flyway 兼容的持久化配置，并避免与自建 Outbox 重复承担同一种责任。

::: tip 建议
**交易关键事件先使用明确的数据库 Outbox；Spring Modulith 负责模块边界、模块事件和架构验证。** 不要在第一版同时实现两套重复的可靠投递机制。
:::
