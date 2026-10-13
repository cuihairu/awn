# 备份与恢复

策略、恢复步骤，以及 2026-10-13 一次完整演练的真实记录。
拓扑与密钥见 `deploy/README.md`，生产加固见 [生产部署手册](./production)。

## 备份策略

- **工具**：`pg_dump -Fc`（自定义归档格式），全库逻辑备份。
- **范围**：同实例，含 `public` 交易表与 `intelligence` schema，一次覆盖，不需要分库策略。
- **频率**：每日一次；任何破坏性变更（删列、改类型、大批量 UPDATE、回滚镜像）前手动加一次。
- **保留**：≥7 天每日份 + 每周归档 ≥4 份，异机存放（宿主机本机目录不合格）。
- **角色不随备份走**：数据库级 dump 不含集群角色（`awn_intel`），恢复后必须补授权——见恢复步骤第 3 步。

```cron
# 每日 03:20 全库备份，保留 7 天
20 3 * * * cd /opt/awn/deploy && docker compose exec -T postgresql pg_dump -Fc -U awn_app awn \
            > /mnt/backup/awn-full-$(date +\%Y\%m\%d-\%H\%M).dump
0 4 * * 0 find /mnt/backup -name 'awn-full-*.dump' -mtime +28 -delete
```

## 恢复步骤

```bash
# 1. 准备同版本 PostgreSQL（16-alpine），干净数据卷
docker run -d --name awn-restore -e POSTGRES_DB=awn -e POSTGRES_USER=awn_app \
       -e POSTGRES_PASSWORD=<与生产相同> postgres:16-alpine

# 2. 导入归档（--no-owner 避免对象归属错误）
docker cp awn-full-*.dump awn-restore:/tmp/restore.dump
docker exec awn-restore sh -c 'pg_restore -U awn_app -d awn --no-owner /tmp/restore.dump'

# 3. 补回集群级角色与授权（dump 不含角色，restore 日志会有 role does not exist 告警）
docker cp deploy/config/postgres/init-intel.sql awn-restore:/tmp/
docker exec awn-restore psql -U awn_app -d awn -f /tmp/init-intel.sql

# 4. 校验（见下）；通过后用生产镜像指向恢复库启动一次应用做端到端验证
```

## 恢复校验清单

```sql
-- 迁移状态：按 installed_rank 取最新，不要 max(version)
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

-- 行数抽样：交易与台账关键表
SELECT count(*) FROM product; SELECT count(*) FROM inventory_pool;
SELECT count(*) FROM inventory_balance; SELECT count(*) FROM inventory_transaction;
SELECT count(*) FROM orders; SELECT count(*) FROM audit_logs;

-- 情报角色权限边界（跨 schema 授权在 --no-owner 下需复查）
SELECT has_table_privilege('awn_intel', 'orders', 'INSERT');   -- 期望 f
SELECT count(*) FROM intelligence.data_sources;                  -- 期望可读
```

应用级验证：用生产镜像指向恢复库启动，`/actuator/health` 为 UP、
商品/库存查询返回已备份的数据、运营接口无令牌时 401。

## 演练记录（2026-10-13）

| 项 | 结果 |
|---|---|
| 源环境 | docker compose（postgresql 16-alpine + awn-api 单副本），库内含交易表与 intelligence schema |
| 备份 | `pg_dump -Fc -U awn_app awn`，耗时 26s，归档 156K（开发量数据），含 33 个 TABLE DATA 段 |
| 恢复 | 全新 `postgres:16-alpine` 容器，`pg_restore --no-owner --role=awn_app`，耗时 54s |
| 恢复告警 | 仅 `role "awn_intel" does not exist`（角色不随库级 dump）→ 重跑 `init-intel.sql` 补齐 |
| 行数比对 | product / inventory_pool / inventory_balance / inventory_transaction / orders / audit_logs / alerts / outbox 全部一致 |
| 迁移状态 | 10 个版本全部 success（按 `installed_rank` 校验） |
| 权限复查 | `has_table_privilege('awn_intel','orders','INSERT') = f`，intelligence schema 可读——隔离边界随恢复保持 |
| 应用验证 | 生产镜像指向恢复库启动：health UP、商品查询返回已备份数据、运营接口 401、专场写接口无令牌 401 |

**演练暴露的三个注意点**（已并入本手册）：

1. **角色不随 dump 走**：`pg_dump` 是库级，`awn_intel` 需恢复后重建并复查跨 schema 授权；
   若生产启用 WAL 归档，建议同时每日留一份 `pg_dumpall -g`。
2. **`flyway_schema_history.version` 是文本**：`max(version)` 会把 `'9'` 判为最新（字典序大于 `'10'`），
   必须按 `installed_rank` 校验。
3. **对账覆盖不到总量**：库存对账只校验 reserved / sold 与预留记录，不校验 `total` 与 ADJUST 流水的累计，
   因此 2026-10-13 压测发现的「调整丢失更新」不会被定时对账发现（缺陷已修，见 [压测报告](../testing/load-test-report)）。

## 限制

- **无 PITR**：当前为逻辑备份，恢复粒度是「最近一次备份点」，分钟级 RPO 需要 WAL 归档（未启用）。
- 备份的是**逻辑对象**：恢复到不同小版本可能触发类型/扩展兼容问题，按上面的版本约束操作。
- 演练环境为单机容器，不含真实数据量；容量与耗时随数据量线性增长，需在真实量级上重测一次。
