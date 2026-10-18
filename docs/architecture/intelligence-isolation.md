# 情报数据隔离

情报只采集与分析公开数据，不得写入交易表。交易库存的可信度以 PostgreSQL 为唯一来源。

## 情报数据流程

```mermaid
flowchart LR
    A["采集调度器"] --> B["来源适配器"]
    B --> C["原始观察记录"]
    C --> D["标准化与去重"]
    D --> E["数据质量检查"]
    E --> F["价格历史 / 状态历史"]
    F --> G["趋势分析"]
    G --> H["情报查询 API"]
    H --> I["专场与情报页面"]
```

## 观察记录

每条观察记录至少保存：

```text
source_id
source_event_reference
source_product_reference
observed_at
price
currency
availability_label
raw_payload_reference
normalization_version
quality_status
```

## 数据分类

必须区分以下数据，不允许混用：

- 官方公布票价。
- 第三方公开标价。
- 历史采集价格。
- 外部页面显示的余票状态。
- AWN 自己的真实可售价格。
- AWN 自己的可售库存。

**外部页面显示"还有 10 张"不能转化成 AWN 库存中的 10 张。**

## 隔离方案

初期可以共享 PostgreSQL 实例，但应采用**独立的情报 Schema** 或清晰独立的表组，并使用单独的数据库访问权限：

- 情报角色 `awn_intel` 只获得情报 schema 的最小权限；交易侧受限角色 `awn_trading` 已建立（V12 迁移 + `TradingRoleIsolationTests` 验证，9fbd92e），应用改连该角色属部署收紧步骤（见 `docs/deployment/production.md`）。
  直接改连会因运行时单元未拆而中断情报读写，拆分方案与分期见 [运行时账号收紧方案](/design/dual-datasource-split-plan)（待拍板，未实现）。
- 情报 Worker 的数据库角色**不能写入**订单、库存、支付和退款表。
- 情报接口只向页面提供经过标准化的观察结果和分析数据。
- 交易商品的价格与库存仍由交易服务决定。

当情报数据量显著增长后，可以将情报数据库独立部署，甚至采用独立的分析存储；无需现在就为此引入分布式系统。
