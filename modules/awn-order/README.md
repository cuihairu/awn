# awn-order

订单创建、状态机、订单快照

## 边界

- **职责**：订单创建、状态机、订单快照
- **禁止**：绕过库存模块扣减库存
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
