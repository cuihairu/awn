# awn-identity

用户、登录、角色、授权

## 边界

- **职责**：用户、登录、角色、授权
- **禁止**：直接修改订单和库存
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
