# deploy/ — 部署编排

单机 docker compose 拓扑，覆盖 Phase 00 目标形态；Kubernetes 化属于后续演进（见 docs/implementation-plan.md Phase 05）。

```
caddy :80/:443 ──┬─ web:3000        网页端（Next.js）
                 ├─ admin:80         管理后台（Vite 静态）
                 └─ awn-api:8080     模块化单体 API
awn-worker                        定时任务 + Outbox 分发（actuator :8081）
postgresql:5432                   库存唯一权威（Flyway 迁移，情报独立 schema）
redis:6379                        缓存 / 分布式锁
rustfs:9000                       图片与素材对象存储（S3 兼容，RustFS；控制台 :9001 仅内网）
```

## 首次部署

```bash
cd deploy
cp .env.example .env      # 填写 AWN_DB_PASSWORD / RUSTFS_SECRET_KEY 等
# 修改 config/postgres/init-intel.sql 中的情报库占位密码，与 AWN_INTEL_PASSWORD 一致
# 修改 config/caddy/Caddyfile 中的域名占位
docker compose up -d --build
```

> RustFS 数据落在命名卷 `rustfs_data`。如需宿主机目录挂载，先 `chown -R 10001:10001 <dir>`（容器以非 root 用户 rustfs 运行）。控制台 http://<内网IP>:9001 不要对外暴露。

## 关键约束

- **PostgreSQL 是库存唯一权威**：禁止引入库存相关的外部缓存写路径，Redis 只做读缓存与锁。
- **情报隔离**：`intelligence` schema 使用独立角色 `awn_intel`，对交易表零权限（`config/postgres/init-intel.sql`）。
- **Outbox 分发**：由 awn-worker 的 `outbox-dispatch` 任务轮询，无 Kafka 依赖（docs/architecture/async-outbox.md）。
- **幂等**：所有写接口要求 `Idempotency-Key`，Worker 任务自身也按任务周期幂等（docs/architecture/payment-state-machine.md）。
