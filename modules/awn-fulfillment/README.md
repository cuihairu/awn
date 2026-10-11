# awn-fulfillment

出票、交付、交付验证

## 边界

- **职责**：出票、交付、交付验证
- **禁止**：把待交付订单直接标记为完成
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
