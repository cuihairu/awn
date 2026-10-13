<div align="center">

<img src="docs/public/logo.svg" alt="AWN logo" width="64"/>

# AWN · 麦芒网

票务交易与情报平台

[![ci](https://github.com/cuihairu/awn/actions/workflows/ci.yml/badge.svg)](https://github.com/cuihairu/awn/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-6db33f?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-6db33f?logo=springboot&logoColor=white)
![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-1.4.2-6db33f?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169e1?logo=postgresql&logoColor=white)
![pnpm](https://img.shields.io/badge/pnpm-10-f9ad00?logo=pnpm&logoColor=white)
![Flutter](https://img.shields.io/badge/Flutter-client-02569b?logo=flutter&logoColor=white)
![License](https://img.shields.io/badge/License-Apache--2.0-2563eb)

[English](README.md) | [简体中文](README.zh-CN.md)

</div>

麦芒网（AWN）是票务交易与情报平台。Java 21 + Spring Boot 模块化单体为交易核心，PostgreSQL 为库存唯一权威，多端复用统一 API 契约（`/api/v1`）。

完整设计文档：`pnpm docs:dev` 启动 VitePress 站点（含架构、API 契约、实施计划、ADR 与原型图）。

## 目录结构

```
├─ docs/                  设计文档站（VitePress + mermaid，首页含原型图）
├─ modules/               Spring Modulith 业务模块（Maven 多模块）
│  ├─ awn-shared            共享内核：通用基础设施、统一异常模型
│  ├─ awn-identity          账号与认证
│  ├─ awn-event             活动、场次、场馆
│  ├─ awn-catalog           演出 / 票档目录
│  ├─ awn-campaign          专场：配置驱动渲染 + 不可变发布版本
│  ├─ awn-inventory         库存池：预留、确认、释放（唯一权威在 PostgreSQL）
│  ├─ awn-order             订单状态机 + Outbox
│  ├─ awn-payment           收单、退款、对账
│  ├─ awn-fulfillment       出票与交付
│  ├─ awn-marketplace       卖家、挂牌、供货审核
│  ├─ awn-promotion         营销与优惠券
│  ├─ awn-intelligence      情报：独立 schema，禁止写交易表
│  ├─ awn-notification      站内信 / 短信 / 订阅消息
│  └─ awn-audit             操作审计、敏感业务操作记录
├─ apps/
│  ├─ awn-api             模块化单体 API（:8080）
│  ├─ awn-worker          Worker：库存超时、订单超时、对账、Outbox 分发（actuator :8081）
│  ├─ web                 网页端（Next.js 15）
│  ├─ admin               管理后台（Vite 7 + React）
│  ├─ miniprogram         小程序（Taro 4 + React，微信 weapp）
│  └─ client              移动客户端（Flutter，iOS / Android）
├─ packages/              前端共享包（@awn/ui、@awn/campaign-renderer、
│                           @awn/ticket-components、@awn/api-client）
├─ design-system/awn/     设计系统 Master + 页面级覆盖（ui-ux-pro-max 产出）
├─ deploy/                docker compose 编排 + Caddy 配置 + Dockerfile
└─ pom.xml                Maven 父 POM（Spring Boot 3.5.6 / Modulith 1.4.2 / Java 21）
```

## 快速开始

| 端 | 命令 | 说明 |
|---|---|---|
| 文档站 | `pnpm install && pnpm docs:dev` | http://localhost:5173 |
| API | `mvn -pl apps/awn-api spring-boot:run`（需本地 PostgreSQL + Redis） | http://localhost:8080/actuator/health |
| 网页端 | `pnpm --filter @awn/web dev` | http://localhost:3000 |
| 管理后台 | `pnpm --filter @awn/admin dev` | http://localhost:5174 |
| 小程序 | `pnpm --filter @awn/miniprogram dev:weapp` | 微信开发者工具导入 `apps/miniprogram` |
| 客户端 | `cd apps/client && flutter run` | 需 Flutter SDK ≥ 3.8 |
| 一键部署 | `cd deploy && cp .env.example .env && docker compose up -d --build` | 见 `deploy/README.md` |
| 一条命令构建 | `scripts/build.sh` | 后端 fat jar + `apps/web/.next/` + `apps/admin/dist/` + 文档站；失败非零退出 |
| 一键验证 | `docker compose -f deploy/docker-compose.yml up -d --build` 后 `curl -s http://127.0.0.1:18080/actuator/health` 返回 UP | 冒烟与细节见 `deploy/README.md` |

## 实施节奏

Phase 00（地基：仓库、CI、模块骨架、Flyway、Testcontainers）→ 01 库存与订单 → 02 支付与履约 → 03 专场与营销 → 04 情报 → 05 性能与容量 → 06 走向平台化。任务书与验收标准见 `docs/implementation-plan.md`。

## 约束速记

1. 库存只认 PostgreSQL，原子条件 UPDATE 扣减；2. 订单 + 预留 + Outbox 同事务；3. 支付 / 退款全程幂等（`Idempotency-Key`）；4. 专场只渲染已发布版本，模块白名单校验；5. 情报与交易同实例分库边界（独立 `intelligence` schema + 独立角色 `awn_intel`），情报永不回写交易表；6. 模块间只走 `api` 包或事件，禁止跨模块摸表。
