# deploy/ — 部署编排

单机 docker compose 拓扑；Kubernetes 化属于后续演进（当前不引入编排层，见 docs/architecture/system-overview.md「部署」行）。

```
caddy :80/:443 ──┬─ web:3000        网页端（Next.js）
                 ├─ admin:80         管理后台（Vite 静态）
                 └─ awn-api:8080     模块化单体 API
awn-worker                        定时任务 + Outbox 分发（无 HTTP 端口，进程存活即健康）
postgresql:5432                   库存唯一权威（Flyway 迁移，情报独立 schema）
redis:6379                        缓存 / 分布式锁
rustfs:9000                       图片与素材对象存储（S3 兼容，RustFS；控制台 :9001 仅内网）
```

## 首次部署

```bash
cd deploy
cp .env.example .env      # 填写所有必需密钥，包括 AWN_ADMIN_TOKEN / AWN_API_TOKEN_SECRET
# 修改 config/postgres/init-intel.sql 中的情报库占位密码，与 AWN_INTEL_PASSWORD 一致
# 修改 config/caddy/Caddyfile 中的域名占位
docker compose up -d --build
```

出口网络受限（Maven Central 按指纹拦截）时，构建前在 `.env` 里加一行：
`CENTRAL_MIRROR=https://maven.aliyun.com/repository/central`

## 一键验证（冒烟）

```bash
docker compose up -d --build
docker compose ps                 # 等 awn-api / postgresql 显示 healthy
curl -s http://127.0.0.1:18080/actuator/health
# 预期：{"status":"UP",...}

curl -s -w '\nHTTP %{http_code}\n' http://127.0.0.1:18080/api/v1/products/smoke-nonexistent
# 预期：统一错误信封 {"code":"NOT_FOUND",...,"traceId":...} + HTTP 404

docker compose exec postgresql psql -U awn_app -d awn -tAc \
  "select max(version) from flyway_schema_history"
# 预期：输出最新迁移版本号（空库启动时 Flyway 已自动执行全部迁移）
```

> RustFS 数据落在命名卷 `rustfs_data`。如需宿主机目录挂载，先 `chown -R 10001:10001 <dir>`（容器以非 root 用户 rustfs 运行）。控制台 http://<内网IP>:9001 不要对外暴露。

## 关键约束

- **PostgreSQL 是库存唯一权威**：禁止引入库存相关的外部缓存写路径，Redis 只做读缓存、锁与限流计数。
- **管理面限流**：`AdminAuthFilter` 在鉴权通过后按资源组（`/api/v1/{resource}`）限流，默认 100 次/60s，超限返回 429；
  存储由 `AWN_RATELIMIT_STORE` 选择（`redis` 多实例共享额度，compose 默认；`memory` 进程内，开发/测试默认）。
  限流组件故障时 fail-open，不阻断管理面。
- **情报隔离**：同实例下 `intelligence` schema 使用独立角色 `awn_intel`，对交易表零权限（`config/postgres/init-intel.sql`；验证见 `IntelligenceIsolationTests`）。是权限边界，不是物理隔离。
- **账号权限现状**：compose 的 `POSTGRES_USER: awn_app` 使 api/worker 运行账号即实例引导超级用户——超级用户不受权限检查约束，可读写 `intelligence` schema（`awn_intel → 交易表` 方向的零权限不受影响）。受限交易角色 `awn_trading` 已由 `V12__trading_role.sql` 建立（仅业务表读写、无 CREATE、情报 schema 零权限，验证见 `TradingRoleIsolationTests`）。生产收紧路径：用独立管理员账号执行初始化，api/worker 改连 `awn_trading`，Flyway 以管理员账号执行，并替换 `awn_trading` 与 `awn_intel` 的占位密码。
- **买家 / 卖家鉴权**：`POST /api/v1/auth/register` 与 `/login` 返回一小时 Bearer token；订单、支付、退款、履约、候补、争议和卖家端点按 token subject 做归属校验。生产必须配置 `AWN_API_TOKEN_SECRET`（至少 32 字节）与 `AWN_ADMIN_TOKEN`；`prod` profile 不接受缺省值。
- **Outbox 分发**：由 awn-worker 的 `outbox-dispatch` 任务轮询，无 Kafka 依赖（docs/architecture/async-outbox.md）。
- **幂等**：创建订单与退款要求 `Idempotency-Key`，支付创建按订单串行化并拒绝已有在途 / 成功支付（docs/api/contracts.md「幂等键规则」）；Worker 任务自身也按任务周期幂等（docs/architecture/payment-state-machine.md）。
