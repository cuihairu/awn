# 部署结构

部署采用 Docker Compose，保持小巧、易部署、易恢复：

```text
Docker Compose
├── reverse-proxy（Caddy，80/443）
├── web（Next.js，3000）
├── admin（Vite 静态，5174）
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
| `AWN_ADMIN_TOKEN` | 运营接口令牌（生产必须覆盖开发默认值） |
| `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY` | 对象存储凭证 |
| `AWN_INTEL_PASSWORD` | 情报库角色密码（与 `init-intel.sql` 占位同步） |

前端可以独立构建与部署；API 和 Worker 共享后端代码，但使用不同启动配置和任务开关。

多端构建产物：

| 端 | 产物 | 部署方式 |
|--------|--------|--------|
| 网页端 | Next.js 构建 | Node 服务或静态 + SSR 容器 |
| 管理后台 | 静态资源 | 对象存储 / CDN 或反向代理 |
| 小程序 | 微信等平台代码包 | 各平台发布流程 |
| 客户端 | iOS / Android 安装包 | 应用商店 / 内部分发 |

## 秘密管理

生产环境应将数据库凭证、支付密钥和其他秘密信息放入受控的环境变量或秘密管理系统，**不要提交到 Git**。

## 运维要求

必须具备：

- PostgreSQL 定期备份。
- 定期恢复演练。
- 数据库迁移回滚策略。
- API 与 Worker 健康检查。
- 库存异常、支付异常和对账差异告警。
- 订单、支付、退款和库存操作的审计。
- 应用日志脱敏。
- 对高风险 API 的限流与访问控制。

## 合规提示

::: warning
正式面向中国用户提供票务交易前，还需要根据具体经营和供货模式完成相应的法律合规审查，确认平台资质、票品来源、实名制、转让规则、退款规则、支付安排及个人信息处理要求。
:::
