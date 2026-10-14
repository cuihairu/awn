# 模块边界

后端采用**单个 Maven 多模块工程**。部署时仍然只有一个 API 应用和一个 Worker 进程；后者运行相同的代码包，只启用后台任务。

## 仓库结构

```text
awn/
├── apps/                        # 所有可运行应用
│   ├── awn-api/                 # Spring Boot API 应用
│   ├── awn-worker/              # Spring Boot Worker 应用
│   ├── web/                     # 网页端（Next.js）
│   ├── admin/                   # 运营管理后台（React + Vite）
│   ├── miniprogram/             # 小程序（Taro + React）
│   └── client/                  # 客户端（Flutter）
├── packages/                    # 共享前端包
│   ├── ui/                      # 基础 UI 组件
│   ├── campaign-renderer/       # 专场模板渲染器
│   ├── ticket-components/       # 统一商品 / 票档组件
│   └── api-client/              # API 客户端与类型
├── modules/                     # Java 业务模块
│   ├── awn-identity/
│   ├── awn-event/
│   ├── awn-catalog/
│   ├── awn-campaign/
│   ├── awn-inventory/
│   ├── awn-order/
│   ├── awn-payment/
│   ├── awn-fulfillment/
│   ├── awn-marketplace/
│   ├── awn-promotion/
│   ├── awn-intelligence/
│   ├── awn-notification/
│   ├── awn-audit/
│   └── awn-shared/
├── deploy/                      # 部署结构
│   ├── docker-compose.yml
│   └── config/
├── docs/                        # VitePress 架构与实施文档
└── pom.xml
```

## 模块职责

| 模块 | 主要职责 | 不允许做的事 |
|--------|--------|--------|
| `identity` | 用户、登录、角色、授权（注册 / BCrypt 凭据校验 / 角色授予已落地；HTTP 会话属 API 层后续） | 直接修改订单和库存 |
| `event` | 活动、场次、场馆 | 处理购买和支付 |
| `catalog` | 商品、票档、商品规则、价格信息 | 自己维护另一套库存 |
| `campaign` | 专场、页面配置、版本发布 | 实现独立的下单逻辑 |
| `inventory` | 库存池、预留、确认、释放 | 直接调用外部支付接口 |
| `order` | 订单创建、状态机、订单快照（数量型订单行走库存池；凭证型订单行走挂牌票品，G2） | 绕过库存模块扣减库存 |
| `payment` | 支付、回调、退款、渠道对账 | 仅凭前端参数确认支付成功 |
| `fulfillment` | 出票、交付、交付验证 | 把待交付订单直接标记为完成 |
| `marketplace` | 卖家、挂牌、供货审核 | 未经确认就创建平台可信库存 |
| `promotion` | 优惠券、活动优惠、使用限制（券模板/发放/核销已落地；与订单金额联动属后续裁决） | 直接修改订单最终金额 |
| `intelligence` | 采集、标准化、价格历史和分析 | 写入交易库存、订单和支付表 |
| `notification` | 站内信、邮件、短信适配（站内信已落地；邮件 / 短信通道未接入） | 让通知失败回滚已经成功的订单 |
| `audit` | 操作审计、敏感业务操作记录 | 保存明文密钥和支付敏感信息 |

## 模块内部结构

每个业务模块内部按 `api` / `application` / `domain` / `infrastructure` 四层组织，不设统一 `service` 目录。

以库存模块为例：

```text
awn-inventory/
└── src/main/java/site/awn/inventory/
    ├── package-info.java
    ├── api/
    │   ├── InventoryFacade.java
    │   ├── CreatePoolCommand.java
    │   ├── ReserveInventoryCommand.java
    │   ├── ReservationView.java
    │   ├── BalanceView.java
    │   ├── PoolView.java
    │   ├── ReconciliationReport.java
    │   ├── ReconciliationViolation.java
    │   ├── ReservationExpiryResult.java
    │   ├── SeatView.java
    │   └── CreateSeatsCommand.java
    ├── application/
    │   ├── InventoryFacadeImpl.java
    │   ├── InventoryWriteService.java
    │   ├── ReservationLifecycleService.java
    │   ├── ReservationExpiryService.java
    │   ├── InventoryReconciliationService.java
    │   ├── InventoryQueryService.java
    │   └── PlatformSeatService.java
    ├── domain/
    │   ├── InventoryPool.java
    │   ├── InventoryBalance.java
    │   ├── InventoryReservation.java
    │   ├── InventoryTransaction.java
    │   ├── LedgerType.java
    │   ├── ReservationStatus.java
    │   ├── PlatformSeat.java
    │   ├── SeatStatus.java
    │   ├── PoolNotFoundException.java
    │   ├── InsufficientInventoryException.java
    │   ├── InventoryConflictException.java
    │   ├── SeatNotFoundException.java
    │   └── package-info.java
    └── infrastructure/
        ├── InventoryPoolRepository.java
        ├── InventoryBalanceRepository.java
        ├── InventoryReservationRepository.java
        ├── InventoryTransactionRepository.java
        └── PlatformSeatRepository.java
```

各模块只公开 `api` 中有意提供给其他模块使用的接口。其他模块**不应**直接引用其 `infrastructure` 或内部领域实现。

## 依赖规则

- 模块间只能通过目标模块的 `api` 包调用，禁止直接引用内部实现。
- `shared` 模块只放通用基础设施与工具，不允许出现业务语义。
- Spring Modulith 的模块结构测试必须在 CI 中执行，任何越界依赖都会导致构建失败。
- 未经架构决策（ADR）确认，不允许为了"解耦"提前拆分微服务。
