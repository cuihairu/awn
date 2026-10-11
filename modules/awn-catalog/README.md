# awn-catalog

商品、票档、商品规则、价格信息

## 边界

- **职责**：商品、票档、商品规则、价格信息
- **禁止**：自己维护另一套库存
- 对外仅暴露 `api` 包中的接口；`application` / `domain` / `infrastructure` 为模块内部实现。

详见 [模块边界文档](../../../docs/architecture/module-boundaries.md)。
