# awn-marketplace

卖家、挂牌、供货审核

## 边界

- **职责**：卖家、挂牌、供货审核
- **禁止**：未经确认就创建平台可信库存
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
