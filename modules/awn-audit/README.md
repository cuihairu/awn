# awn-audit

操作审计、敏感业务操作记录

## 边界

- **职责**：操作审计、敏感业务操作记录
- **禁止**：保存明文密钥和支付敏感信息
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
