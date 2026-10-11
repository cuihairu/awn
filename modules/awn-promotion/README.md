# awn-promotion

优惠券、活动优惠、使用限制

## 边界

- **职责**：优惠券、活动优惠、使用限制
- **禁止**：直接修改订单最终金额
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
