# 生产部署手册

单机 docker compose 部署的完整操作手册。拓扑与首次快速上手见 `deploy/README.md`；
本文是面向生产环境的版本：密钥、升级、回滚、备份、监控与已知限制。
备份恢复的操作细节与一次完整演练记录见 [备份与恢复](./backup-restore)，
性能基线见 [压测报告](../testing/load-test-report)。

## 前置条件

- 一台 2C4G 以上的 Linux 主机（单机部署口径），已安装 Docker Engine + compose 插件。
- 80/443 端口可用，域名已解析到主机（HTTPS 由 Caddy 自动签发续期）。
- 出口网络：构建期需要 Maven 仓库；被拦截时在 `.env` 设
  `CENTRAL_MIRROR=https://maven.aliyun.com/repository/central`（坐标与校验不变）。

## 密钥与配置（.env）

`cd deploy && cp .env.example .env` 后**必须逐项填写**：

| 变量 | 用途 | 生产要求 |
|---|---|---|
| `AWN_DB_PASSWORD` | PostgreSQL 引导账号 `awn_app` 密码 | 强随机；postgresql 容器与 Flyway 迁移连接同源引用，应用运行时不使用 |
| `AWN_TRADING_DB_PASSWORD` | 交易运行账号 `awn_trading` 密码 | 强随机；api/worker 主源与 `V29` 迁移占位符同源引用 |
| `AWN_INTEL_DB_PASSWORD` | 情报运行账号 `awn_intel` 密码 | 强随机；initdb 建号与 api/worker 情报源同源引用 |
| `RUSTFS_SECRET_KEY` | 对象存储密钥 | 强随机 |
| `AWN_ADMIN_TOKEN` | 运营接口令牌 | 必填；Compose 与 `prod` profile 缺失时拒绝启动，不回落开发默认值 |
| `AWN_API_TOKEN_SECRET` | 买家 / 卖家 Bearer token 的 HMAC 密钥 | 必填；至少 32 字节，轮换会使现有 token 失效 |
| `CENTRAL_MIRROR` | Maven 镜像（可选） | 仅出口受限环境需要 |

三个数据库账号密码全部由 `.env` 注入，不再改仓库内文件（三个变量在 Compose 里均以 `:?` 强制，缺失即拒绝启动）。唯一还需修改的仓库内文件：

1. `config/caddy/Caddyfile`：把域名占位换成真实域名。

## 首次部署

```bash
cd deploy
cp .env.example .env            # 填密钥（见上表）
# 修改 config/caddy/Caddyfile 域名
docker compose up -d --build
docker compose ps               # 等 awn-api / postgresql healthy
curl -s http://127.0.0.1:18080/actuator/health   # 预期 {"status":"UP",...}
```

空库首次启动的初始化顺序（postgres 容器 initdb 钩子 → api 进程）：

1. `10-init-intel.sh`（`docker-entrypoint-initdb.d`，仅空卷首启执行）以 psql 变量把
   `AWN_INTEL_DB_PASSWORD` 注入 `init-intel.sql`，建 `intelligence` schema 与最小权限角色 `awn_intel`。
2. Flyway 以引导账号 `awn_app` 执行全部迁移（`select max(version) from flyway_schema_history` 可核）：
   `V12` 建受限交易角色 `awn_trading`，`V29` 按其密码占位符对齐到 `AWN_TRADING_DB_PASSWORD`。
3. api / worker 才启动：交易数据源以 `awn_trading`、情报数据源以 `awn_intel`、
   Flyway 以 `awn_app`，三者互不代用。

角色创建幂等但**只有空卷首启执行一次**：已初始化的卷重跑 compose 不会重建角色，
轮换 `AWN_INTEL_DB_PASSWORD` 需要重跑容器初始化脚本（见下「备份与恢复」）。

## 日常升级

```bash
git pull --rebase
docker compose build api worker web
docker compose up -d            # 滚动替换；数据库迁移由 api 启动时 Flyway 前向执行
```

- 迁移只进不退：**数据库不会随镜像回滚**，回滚镜像必须确认旧镜像代码兼容新表结构（加列兼容，删列/改列不兼容）。
- api 与 worker 共库：worker 等待 api healthy 后才启动（compose depends_on），避免迁移竞争。
- 单副本约束：worker 的定时任务（outbox 分发 / 订单超时 / 两类对账）与 Inbox 去重按单副本设计，
  扩多副本前必须先引入任务分片或选主。

## 回滚

```bash
# 回到上一可用镜像（按 tag/摘要），数据库保持现状
docker compose up -d --no-deps --force-recreate api worker
```

数据回滚没有自动化：Flyway 前向-only。破坏性变更上线前必须先执行一次全库备份（见下）。

## 备份与恢复

策略与恢复步骤详见 [备份与恢复](./backup-restore)。要点：

- 每日 `pg_dump -Fc` 全库（含 intelligence schema），保留 ≥7 天 + 每周归档 ≥4 份，异机存放。
- 破坏性变更（删列、改类型、大批量 UPDATE）前手动加一次备份。
- 恢复目标：同版本 PostgreSQL（16-alpine）+ 同版本应用镜像。
- 三个必须知道的坑（2026-10-13 演练实测）：
  1. **角色不随 dump 走**：角色是集群级对象，`pg_dump -Fc` 不含角色定义；恢复后要补回
     `awn_intel`（以 psql 变量注入密码重跑 `init-intel.sql`，容器内路径 `/opt/awn-init/init-intel.sql`）
     与 `awn_trading`（`V12` / `V29` 已登记在 `flyway_schema_history` 不会自动重放，需手工重跑），
     否则跨 schema 授权缺失（restore 日志 `role "awn_intel" does not exist`）或应用连不上库；
     平时用 `pg_dumpall --globals-only` 单独备份角色可免此坑。
  2. **`flyway_schema_history.version` 是文本**：`max(version)` 会把 `'9'` 判为最新，
     必须按 `installed_rank` 校验迁移状态。
  3. 对账不覆盖 `total` 与 ADJUST 流水累计，恢复后的台账一致性要单独查（见压测报告）。
- 完整步骤与一次演练记录见 [备份与恢复](./backup-restore)。

## 监控与告警

- **进程健康**：`GET /actuator/health`（api 有 HTTP 口径；worker 无 HTTP，容器进程存活即健康）。
- **业务告警**：`alerts` 表三类（INVENTORY / PAYMENT / RECONCILIATION），分别由库存对账差异、
  outbox 投递转 FAILED、支付对账差异触发；运营后台异常视图确认（acknowledge）。
- **定时对账**：库存对账（默认 600s）、支付对账（默认 300s）由 worker 驱动；
  Outbox 分发（5s）与订单超时释放（60s）延迟突增通常意味着 worker 卡死或数据库压力。
- 建议在主机侧对 `127.0.0.1:18080/actuator/health` 与 `docker compose ps` 做周期探测。

## 已知限制与加固清单

1. **数据库账号已收紧（2026-10-19 落地）**：api/worker 不再以实例引导超级用户运行。
   交易数据源走 `awn_trading`（`V12__trading_role.sql` 建，仅交易表组读写、无 CREATE、
   对 `intelligence` schema 零权限，密码由 `V29` 按 `AWN_TRADING_DB_PASSWORD` 注入，
   验证见 `TradingRoleIsolationTests`）；情报数据源走 `awn_intel`
   （`intelligence` 读写、交易表零权限，验证见 `IntelligenceIsolationTests`）。
   运行时 JPA 已按数据源拆成两个实体单元，见 [运行时账号收紧方案](../design/dual-datasource-split-plan)。
   **剩余收紧**：Flyway 迁移连接仍用引导账号 `awn_app`（建表与管角色需要高权限，属设计内口径，
   见上述方案 S3）；若要求迁移也不碰超管，需另行收敛迁移账号。
   另注：空卷初始化只在首启发生，**卷已存在时轮换情报密码不会自动生效**，需手工重跑 `init-intel.sql`。
2. **管理面令牌必须配置**（见密钥表）；泄露后更换令牌只需改 `.env` 重建 api/worker。
   管理面口径：`/api/v1/admin/**` 与专场管理写操作（`POST /api/v1/campaigns**`）都需要 `X-Admin-Token`，
   公开读（`GET /api/v1/campaigns**`）不需要。鉴权通过后按资源组限流（默认 100 次/60s），超限 429；
   存储由 `AWN_RATELIMIT_STORE` 选择——compose 默认 `redis`（多实例共享额度），本地/测试默认 `memory`
    （进程内）。限流组件故障时 fail-open，不阻断管理面。
   买家 / 卖家交易端点另要求 `Authorization: Bearer <accessToken>`，token 一小时过期；用户身份及订单 / 卖家归属取服务端校验，不能以请求体 `userId` 替代。
3. **PITR 未启用**：当前为逻辑备份（pg_dump），误删数据只能回到最近一次备份点；
   需要分钟级 RPO 时再评估 WAL 归档。
4. **worker 单副本**为硬约束（见日常升级）。
5. 对象存储控制台（RustFS :9001）只在内网可达，不得经反代对外。
6. 公开端口的实际入口是 Caddy（80/443）；api 仅绑 `127.0.0.1:18080` 供冒烟，不得改为对外发布。
