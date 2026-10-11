# awn-shared

共享内核：通用基础设施、工具与统一异常模型

## 边界

- **职责**：共享内核：通用基础设施、工具与统一异常模型
- **禁止**：不允许出现任何业务语义
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
