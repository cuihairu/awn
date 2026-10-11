# awn-campaign

专场、页面配置、版本发布

## 边界

- **职责**：专场、页面配置、版本发布
- **禁止**：实现独立的下单逻辑
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
