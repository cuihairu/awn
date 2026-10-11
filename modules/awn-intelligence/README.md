# awn-intelligence

采集、标准化、价格历史和分析

## 边界

- **职责**：采集、标准化、价格历史和分析
- **禁止**：写入交易库存、订单和支付表
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
