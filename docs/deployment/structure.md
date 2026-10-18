# 部署结构

单机 Docker Compose 拓扑，8 个服务：

```text
Docker Compose
├── reverse-proxy（Caddy，80/443）
├── web（Next.js，3000）
├── admin（Vite 静态构建，容器内 caddy file-server :80，经反代 /admin 路径；本地开发 `pnpm --filter @awn/admin dev` 才是 5174）
├── awn-api（8080，仅 127.0.0.1:18080 暴露）
├── awn-worker（无端口映射，actuator 8081 仅容器内）
├── postgresql（无端口映射）
├── redis（无端口映射）
└── object-storage（RustFS，S3 兼容；也可替换为外部对象存储服务）
```

对应编排文件位于仓库 `deploy/docker-compose.yml`（含 Caddy 配置与各服务 Dockerfile）。

关键环境变量（`deploy/.env`，不入库）：

| 变量 | 用途 |
|------|------|
| `AWN_DB_PASSWORD` | PostgreSQL 密码（api/worker 共用） |
| `AWN_ADMIN_TOKEN` | 运营接口令牌（**必填**，Compose 以 `:?` 强制，缺失拒绝启动） |
| `AWN_API_TOKEN_SECRET` | 买家 / 卖家 Bearer token 的 HMAC 密钥（**必填**，至少 32 字节） |
| `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY` | 对象存储凭证 |
| `AWN_INTEL_PASSWORD` | 情报库角色密码（与 `init-intel.sql` 占位同步） |
| `AWN_RATELIMIT_STORE` | 管理面限流存储（可选；Compose 默认 `redis`，本地/测试默认 `memory`） |

前端可以独立构建与部署；API 和 Worker 共享后端代码，但使用不同启动配置和任务开关。

多端构建产物：

| 端 | 产物 | 部署方式 |
|--------|--------|--------|
| 网页端 | Next.js 构建 | Node 服务或静态 + SSR 容器 |
| 管理后台 | 静态资源 | 对象存储 / CDN 或反向代理 |
| 小程序 | 微信等平台代码包 | 各平台发布流程 |
| 客户端 | iOS / Android 安装包 | 应用商店 / 内部分发 |

## 秘密管理

密钥只走 `deploy/.env`（`.gitignore` 已排除，不入库）。两处入库文件含占位密码，部署前必须替换为 `.env` 中的真实值：

| 文件 | 占位值 |
|---|---|
| `config/postgres/init-intel.sql` | `change-me-intel-password` |
| `db/migration/V12__trading_role.sql` | `change-me-trading-password` |

## 运维要求

| 项 | 机制 |
|---|---|
| 备份 | 每日 `pg_dump -Fc`，步骤见 [备份与恢复](./backup-restore) |
| 迁移状态 | 按 `installed_rank` 校验 `flyway_schema_history`（`max(version)` 是文本比较，会把 `'9'` 判为最新） |
| 健康检查 | `GET /actuator/health`；worker 无 HTTP 端口，看容器进程存活 |
| 业务告警 | `alerts` 表三类，`category` 取值受 `V8__intelligence_audit_tables.sql` CHECK 约束限定为 `INVENTORY` / `PAYMENT` / `RECONCILIATION`；运营后台 acknowledge |
| 审计 | `audit_logs` 表；日志字段脱敏由 `awn-shared` 的 masking 组件承担 |
| 限流 | 管理面按资源组 100 次/60s，超限 429；组件故障 fail-open |

数据库迁移为前向-only，无自动回滚。破坏性变更（删列、改类型）上线前必须先做一次全库备份。

## 合规提示

::: warning
正式面向中国用户提供票务交易前，还需要根据具体经营和供货模式完成相应的法律合规审查，确认平台资质、票品来源、实名制、转让规则、退款规则、支付安排及个人信息处理要求。
:::
