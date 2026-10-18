<div align="center">

<img src="docs/public/logo.svg" alt="AWN logo" width="64"/>

# AWN · 麦芒网

> [English](README.md) | **中文**

票务交易与情报平台

[![ci](https://github.com/cuihairu/awn/actions/workflows/ci.yml/badge.svg)](https://github.com/cuihairu/awn/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-6db33f?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6db33f?logo=springboot&logoColor=white)
![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-2.1.1-6db33f?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169e1?logo=postgresql&logoColor=white)
![pnpm](https://img.shields.io/badge/pnpm-10-f9ad00?logo=pnpm&logoColor=white)
![Flutter](https://img.shields.io/badge/Flutter-client-02569b?logo=flutter&logoColor=white)
![License](https://img.shields.io/badge/License-Apache--2.0-2563eb)

</div>

麦芒网（AWN）是票务交易与情报平台。Java 21 + Spring Boot 模块化单体为交易核心，PostgreSQL 为库存唯一权威，多端复用统一 API 契约（`/api/v1`）。

完整设计文档：`pnpm docs:dev` 启动 VitePress 站点（含架构、API 契约、实施计划、ADR 与原型图）。

## 目录结构

```
├─ docs/                  设计文档站（VitePress + mermaid，首页含原型图）
├─ modules/               Spring Modulith 业务模块（Maven 多模块）
│  ├─ awn-shared            共享内核：通用基础设施、统一异常模型
│  ├─ awn-identity          账号：注册、BCrypt 凭据校验、角色
│  ├─ awn-event             活动、场次、场馆
│  ├─ awn-catalog           演出 / 票档目录
│  ├─ awn-campaign          专场：配置驱动渲染 + 不可变发布版本
│  ├─ awn-inventory         库存池：预留、确认、释放（唯一权威在 PostgreSQL）
│  ├─ awn-order             订单状态机 + Outbox
│  ├─ awn-payment           收单、退款、对账
│  ├─ awn-fulfillment       出票与交付
│  ├─ awn-marketplace       卖家、挂牌、供货审核
│  ├─ awn-promotion         优惠券：模板、配额发放、核销（与订单金额联动待裁决）
│  ├─ awn-intelligence      情报：独立 schema，禁止写交易表
│  ├─ awn-notification      站内信已落地（邮件 / 短信通道待接入）
│  ├─ awn-audit             操作审计、敏感业务操作记录
│  └─ awn-star              明星图鉴：人物、作品、带身份的关系
├─ apps/
│  ├─ awn-api             模块化单体 API（:8080）
│  ├─ awn-worker          Worker：订单超时、库存/支付对账、Outbox 分发（actuator :8081）
│  ├─ web                 网页端（Next.js 15）
│  ├─ admin               管理后台（Vite 7 + React）
│  ├─ miniprogram         小程序（Taro 4 + React，微信 weapp）
│  └─ client              移动客户端（Flutter 骨架，需先 `flutter create .` 生成平台工程）
├─ packages/              前端共享包（@awn/ui、@awn/campaign-renderer、
│                           @awn/ticket-components、@awn/api-client）
├─ design-system/awn/     设计系统 Master + 页面级覆盖
├─ deploy/                docker compose 编排 + Caddy 配置 + Dockerfile
└─ pom.xml                Maven 父 POM（Spring Boot 4.1.1 / Modulith 2.1.1 / Java 21）
```

## 快速开始

| 端 | 命令 | 说明 |
|---|---|---|
| 文档站 | `pnpm install && pnpm docs:dev` | http://localhost:5173 |
| API | `./mvnw -pl apps/awn-api spring-boot:run`（需本地 PostgreSQL；仅 `AWN_RATELIMIT_STORE=redis` 时才需要 Redis，本地默认进程内限流） | http://localhost:8080/actuator/health |
| 网页端 | `pnpm --filter @awn/web dev` | http://localhost:3000 |
| 管理后台 | `pnpm --filter @awn/admin dev` | http://localhost:5174 |
| 小程序 | `pnpm --filter @awn/miniprogram dev:weapp` | 微信开发者工具导入 `apps/miniprogram` |
| 客户端 | `cd apps/client && flutter run` | 骨架工程：Dart SDK ≥ 3.8（对应 Flutter 3.32+），先 `flutter create .` 生成平台工程 |
| 一键部署 | `cd deploy && cp .env.example .env && docker compose up -d --build` | 见 `deploy/README.md` |
| 一条命令构建 | `scripts/build.sh` | 后端 fat jar + `apps/web/.next/` + `apps/admin/dist/` + 文档站；失败非零退出 |
| 一键验证 | `docker compose -f deploy/docker-compose.yml up -d --build` 后 `curl -s http://127.0.0.1:18080/actuator/health` 返回 UP | 冒烟与细节见 `deploy/README.md` |

## 本地测试口径

CI 与本地开发按改动面三路分流——改一行文档不该全量编译。Maven 版本经 wrapper 钉定（`./mvnw`，3.10.0，首次运行自动下载），本地与 CI 一致。

1. **文档改动**（`*.md`、`docs/**`、图片）：不编译。主 CI workflow 的
   `paths-ignore` 已跳过这些路径。
2. **代码改动**：只跑改动子集，不全量。
   - `./mvnw -pl <改动模块> -am test`
   - `pnpm --filter @awn/<包> test`
3. **全量**（`./mvnw -B -ntp verify -pl apps/awn-api,apps/awn-worker -am`）：仅以下情形触发——pom.xml / pnpm-lock.yaml / package.json、
   `.github/**`、Docker/Compose 配置、发布前。

## 实施节奏

Phase 0（架构基线）→ 01 工程骨架与模块治理 → 02 活动、商品与专场 → 03 库存与订单核心 → 04 支付、退款与可靠异步 → 05 履约与卖家挂牌 → 06 票务情报与运营完善（已完成）。任务书与验收标准见 `docs/implementation-plan.md`。

## 约束速记

1. 库存只认 PostgreSQL，原子条件 UPDATE 扣减；2. 订单 + 预留 + Outbox 同事务；3. 创建订单和退款 HTTP 要求 `Idempotency-Key`，支付创建按订单串行化并拒绝已有在途/成功支付；4. 专场只渲染已发布版本，模块白名单校验；5. 情报使用独立 `intelligence` schema 与角色，但生产 API/Worker 仍以引导超管账号连接，数据库角色边界尚未生效，待运行凭据隔离完成；6. 模块间只走 `api` 包或事件，禁止跨模块摸表。
