# awn-payment

支付、回调、退款、渠道对账

## 边界

- **职责**：支付、回调、退款、渠道对账
- **禁止**：仅凭前端参数确认支付成功
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
