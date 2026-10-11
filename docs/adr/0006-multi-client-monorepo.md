# ADR-0006：多端统一 monorepo 结构（apps/ + packages/ + modules/）

- 状态：已接受
- 日期：2026-10-11

## 背景

AWN 需要同时交付网页端、微信小程序、移动客户端（Flutter）与运营后台，另有 Spring Boot 后端。分散仓库会导致 API 契约与渲染契约漂移。

## 决策

单一 monorepo，目录约定：

```text
awn/
├── apps/            # 所有可运行应用
│   ├── awn-api/       # Spring Boot API（Java）
│   ├── awn-worker/    # Spring Boot Worker（Java）
│   ├── web/           # 网页端：Next.js + React + TS
│   ├── admin/         # 运营后台：React + Vite + TS
│   ├── miniprogram/   # 小程序：Taro 4 + React + TS
│   └── client/        # 客户端：Flutter（用户手机 App）
├── packages/        # 共享前端包（pnpm workspace）
│   ├── ui/              # 基础组件
│   ├── campaign-renderer/ # 专场模板渲染器
│   ├── ticket-components/ # 统一商品/票档组件
│   └── api-client/        # API 客户端与类型
├── modules/         # Java 业务模块（Maven 多模块）
├── deploy/          # Docker Compose 与配置
└── docs/            # VitePress 文档站
```

- JS 侧使用 pnpm workspace 管理（`docs`、`apps/web`、`apps/admin`、`apps/miniprogram`、`packages/*`）。
- Java 侧由根 `pom.xml` 聚合管理。
- Flutter 独立工具链，不入 pnpm/Maven 工作区。
- 各端共享同一 OpenAPI 契约与专场渲染契约（`schemaVersion`）。

## 后果

- 正面：契约单点维护，跨端一致性可审查；重构与全局搜索成本低。
- 负面：仓库体积与 CI 时间增长；需要按目录过滤触发构建。
