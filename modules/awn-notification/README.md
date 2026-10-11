# awn-notification

站内信、邮件、短信适配

## 边界

- **职责**：站内信、邮件、短信适配
- **禁止**：让通知失败回滚已经成功的订单
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
