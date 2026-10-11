# awn-inventory

库存池、预留、确认、释放

## 边界

- **职责**：库存池、预留、确认、释放
- **禁止**：直接调用外部支付接口
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
