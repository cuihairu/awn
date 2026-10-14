# 系统总览

AWN（麦芒网）是一个面向中国用户的**票务交易与情报平台**，采用 **Java + Spring Boot 模块化单体**架构。

本文定义 AWN 的目标架构。各能力的落地状态见 `todo.md` 与 `docs/审计-文档一致性.md`。

## 核心原则

- 专场页面可以千差万别，但**购买链路只有一套**。
- 库存必须通过 PostgreSQL 事务和原子操作保证。
- 支付回调必须幂等。
- 票务情报与真实交易库存严格隔离。

## 最终技术决策

| 层次 | 技术选择 | 设计目的 |
|--------|--------|--------|
| 用户端 | TypeScript + React + Next.js | 活动展示、SEO、专场页面、购票 |
| 小程序 | TypeScript + Taro（React） | 微信等小程序渠道购票 |
| 移动客户端 | Flutter | iOS / Android 用户 App |
| 运营后台 | TypeScript + React | 活动管理、专场编辑、订单运营 |
| 后端 | Java + Spring Boot | 统一业务 API 和交易逻辑 |
| 模块治理 | Spring Modulith | 检查模块依赖、生成架构文档、模块测试 |
| 数据库 | PostgreSQL | 订单、库存、支付和核心业务数据 |
| 数据访问 | Spring Data JPA + JDBC | 普通业务使用 JPA，库存关键操作使用原子 SQL |
| 数据库迁移 | Flyway | 可审计、可重复执行的数据库版本管理 |
| 缓存 | Redis | 缓存、限流、短期辅助状态（限流已用于管理面：AdminAuthFilter 按资源组限流，进程内默认、生产经 AWN_RATELIMIT_STORE=redis 切换多实例共享额度；缓存与短期状态尚未使用） |
| 异步处理 | Spring Worker + PostgreSQL Outbox | 订单超时、通知、对账、履约任务 |
| 消息队列 | 第一阶段暂不引入 | 避免在没有实际需求时增加运维复杂度 |
| 文件存储 | S3 兼容对象存储 | 活动图片、专场素材、受控的交付凭证 |
| API 契约 | OpenAPI | 前后端协作契约（当前以 `docs/api/contracts.md` + `packages/api-client` 共享类型为准，OpenAPI 工件未引入） |
| 测试 | JUnit 5 + Testcontainers | 真实 PostgreSQL 环境下验证事务与并发 |
| 部署 | Docker Compose | 单机拓扑，不引入编排层 |

Spring Modulith 在 CI 中校验模块依赖边界（`ModularityTests`），并生成模块文档。

**不采用微服务作为初始架构**（ADR-0001）。模块有明确代码边界，但部署单元只有 API 与 Worker 两个。

## 系统总体架构图

AWN 系统分为用户展示、交易核心、异步处理和情报数据四个主要部分：

```mermaid
flowchart TB
    subgraph Client["用户与运营端"]
        WEB["用户 Web / H5"]
        MP["小程序 Taro"]
        APP["Flutter 客户端"]
        ADMIN["运营管理后台"]
        SELLER["卖家工作台"]
    end

    subgraph Front["前端展示层"]
        CAMPAIGN["专场模板渲染器"]
        TICKETS["统一商品 / 票档组件"]
        CHECKOUT["统一结算页面"]
    end

    subgraph App["Spring Boot 模块化单体"]
        ID["identity"]
        EVENT["event"]
        CATALOG["catalog"]
        CAMPAIGNMOD["campaign"]
        INVENTORY["inventory"]
        ORDER["order"]
        PAYMENT["payment"]
        FULFILL["fulfillment"]
        MARKET["marketplace"]
        INTELAPI["intelligence"]
        ADMINMOD["admin / audit"]
    end

    subgraph Storage["数据与持久化"]
        PG[("PostgreSQL")]
        REDIS[("Redis")]
        OBJ[("对象存储")]
        OUTBOX["Outbox / Inbox"]
    end

    subgraph Worker["异步工作进程"]
        TASK["Spring Worker"]
        TIMEOUT["订单超时 / 库存释放"]
        RECON["支付 / 退款对账"]
        NOTIFY["通知 / 履约任务"]
    end

    subgraph Intel["情报采集边界"]
        COLLECT["采集调度器（Worker 周期任务：collectAll 全量采集，默认 1800s）"]
        ADAPTER["来源适配器"]
        RAW[("原始观察数据")]
        HISTORY[("标准化历史数据")]
        ANALYTICS["价格趋势 / 渠道分析"]
    end

    WEB --> CAMPAIGN
    WEB --> TICKETS
    WEB --> CHECKOUT
    MP --> CAMPAIGN
    APP --> CAMPAIGN
    ADMIN --> App
    SELLER --> App

    CAMPAIGN --> CAMPAIGNMOD
    TICKETS --> CATALOG
    CHECKOUT --> ORDER
    App --> PG
    App --> REDIS
    App --> OBJ
    App --> OUTBOX

    OUTBOX --> TASK
    TASK --> TIMEOUT
    TASK --> RECON
    TASK --> NOTIFY

    COLLECT --> ADAPTER
    ADAPTER --> RAW
    RAW --> HISTORY
    HISTORY --> ANALYTICS
    ANALYTICS --> INTELAPI
    INTELAPI --> WEB
```

三条不可逾越的边界：

1. 专场只负责展示，不能拥有独立的库存和订单实现。
2. 库存模块是唯一有权执行库存预留、确认和释放的模块。
3. 情报模块只能提供公开数据及分析结果，不能修改交易库存。
