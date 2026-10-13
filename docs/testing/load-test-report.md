# 压测报告（基线）

2026-10-13 单机 docker compose 上的第一轮 HTTP 基线压测。
数字是**该环境的基线**，不是容量承诺：宿主机为共享开发机，api 单副本（G1, Xmx1g），
PostgreSQL 16-alpine 与 api 同机。

## 环境与工具

| 项 | 值 |
|---|---|
| 被测 | `deploy/docker-compose.yml`：awn-api（镜像 `awn-awn-api:latest`）+ postgresql:16-alpine，同机容器 |
| 数据 | 1 个 PUBLISHED 商品 + 1 个池（total 1,000,000），无预留无已售 |
| 工具 | `pnpm dlx autocannon`（Node HTTP 基准），连接数 20（读）/ 8（写），时长 30s / 30s / 20s |
| 口径 | 平均吞吐（req/s）、延迟 p50/p90/p99/max（ms）、2xx / 4xx / 5xx / 错误数 |

## 结果

| 场景 | 吞吐 | p50 | p90 | p99 | max | 错误 |
|---|---|---|---|---|---|---|
| `GET /api/v1/products/{code}`（商品详情） | 356 req/s | 141 | 308 | 617 | 886 | 0（10,682 请求全 2xx） |
| `GET /api/v1/products/{code}/availability`（库存可售摘要） | 249 req/s | 167 | 305 | 499 | 895 | 0（7,480 请求全 2xx） |
| `POST /api/v1/admin/inventory/adjustments`（写路径，修复前） | 74 req/s | 131 | 271 | 590 | 929 | 0（1,471 请求全 2xx） |
| `POST /api/v1/admin/inventory/adjustments`（写路径，修复后） | 48 req/s | 149 | 318 | 628 | 1907 | 0（970 请求全 2xx） |

写路径单次请求 = 1 个事务（余额 UPDATE + ADJUST 流水 INSERT + 审计 INSERT）。

## 压测抓到的缺陷与修复

**调整接口并发丢失更新（P0，已修）**。修复前 `adjustPool` 是「读余额 → 计算新值 → setter 保存」，
非原子：

- 集成测试红：16 线程 × 25 次 `+1`，期望净增 400，实际只 +34（91.5% 更新丢失）；
- 实测佐证：线上 8 并发 1,479 次 `+1`，台账记满 1,479 行，计数器只净增 278。

这违反余额实体自身的不变量注释（「所有变更只允许走原子条件 UPDATE，禁止 setter」）。
修复：`InventoryBalanceRepository.adjustTotal` 原子条件 UPDATE（守卫 `total + delta >= reserved + sold`，
`version + 1`），服务层去掉读改写。修复后同测试 500/500 精确，live 复测 978 次请求 978 次精确生效。
commit `eb35c32`，代价是写路径吞吐 74 → 48 req/s（运营低频接口，正确性优先）。

**连带发现的对账盲区（未修，已记录 todo）**：库存对账只校验 reserved / sold 与预留记录，
不校验 `total` 与 ADJUST 流水累计。缺陷期间台账与计数器漂移 1,201，无任何告警。

## 瓶颈结论

1. **写路径是行锁串行**：单池余额行上的条件 UPDATE 天然串行化，吞吐上限由单行更新速度决定——
   这是 ADR-0002 已接受的取舍；热点池扩容需评估分片，不得以牺牲正确性为代价。
2. **读路径 p99 抖动来自环境**：同机 PG + 共享宿主机 CPU 下 p99 在 0.5–0.9s 波动，p50 在 140–170ms；
   需要更干净的基线时应在独占资源上重测，但相对量级（availability 比 detail 低 ~30%，
   因其多一次池 + 余额联查）已经可信。
3. **调整接口保持低频**：修复后 48 req/s 对运营操作足够；不要把它当批量导入通道，
   批量调整应走脚本单线程或事务内多行。

## 未覆盖

- **下单链路无 HTTP 端点**（`OrderLineCommand` 只在 Facade 层可达），HTTP 级下单压测待接口落地后补；
  防超卖正确性目前由真实 PG 并发集成测试兜底（`InventoryConcurrencyTests` 24 线程抢 10 张、
  `InventoryStressTests` 混合压测后恒等式成立、超卖为 0）。
- 支付渠道为 sandbox，无真实渠道延迟；缓存层（Redis）未启用，读路径全量走库。

## 复现

```bash
cd deploy && docker compose up -d postgresql awn-api
# 建压测数据（商品 + 池）后：
pnpm dlx autocannon -c 20 -d 30 http://127.0.0.1:18080/api/v1/products/<code>
pnpm dlx autocannon -c 8 -d 20 -m POST -H 'X-Admin-Token: <token>' \
  -H 'Content-Type: application/json' \
  -i body.json http://127.0.0.1:18080/api/v1/admin/inventory/adjustments
```
