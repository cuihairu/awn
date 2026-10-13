# 测试清单

## 本机运行集成测试

集成测试需要本机可用的 Docker daemon。Docker Engine 29+ 拒绝旧客户端 API 1.32，
而 Testcontainers 内置 docker-java 在未显式配置时以 API 1.32 握手，报错
`client version 1.32 is too old. Minimum supported API version is 1.44`。
解决方法：在 `~/.docker-java.properties` 写入 `api.version=1.44`（Docker 25+ 均支持），
Testcontainers 检测到显式版本后不再回退到 1.32。

## 防超卖测试

测试环境使用**真实 PostgreSQL**（Testcontainers），而不是用 H2 或模拟 Repository 替代。

| 场景 | 验收标准 |
|--------|--------|
| 库存 10 张，24 线程并发抢占（`InventoryConcurrencyTests`） | 恰好 10 个请求成功，其余库存不足，无超卖 |
| 库存 50 张，32 线程 × 12 轮混合负载（`InventoryStressTests`） | 请求守恒、全程无负余额、恒等式成立、sold = 确认数 |
| 相同幂等键重复提交 | 不创建重复订单 |
| 订单事务中途失败 | 订单和库存预留整体回滚 |
| 同一预留被重复释放（重放幂等，顺序覆盖） | 库存只释放一次 |
| 同一独立票品并发购买 | 最多一个有效订单占用（`VoucherOrderModuleTests`：并发抢单守卫只放行一单，a418f39） |
| 平台座位并发抢占（`PlatformSeatModuleTests`） | 16 线程并发抢座只放行一单，逐座状态机守卫裁决（c7ea49c） |
| 订单取消与支付确认竞争 | 只有合法状态转换成功 |
| 支付结果未知 | 不会因为简单超时而错误释放库存（部分覆盖：超时释放后回调置 UNKNOWN 不盲目确认；订单侧拦截未实现，见 trading-model G6） |
| 库存对账 | 对账覆盖 reserved / sold / total 三类口径，total 漂移产出 `TOTAL_MISMATCH`（`InventoryReconciliationTests`） |

## 支付与恢复测试

必须覆盖：

- 重复支付回调。
- 金额与订单不一致。
- 支付渠道超时但实际扣款。
- 支付成功后应用崩溃（经重启 / 事件重复分发覆盖；无 JVM 崩溃注入）。
- Outbox 消息重复消费。
- 退款回调重复到达。
- 部分退款累计金额校验。
- 支付成功但库存已被异常释放。
- 对账发现资金记录与订单状态不一致。

## 专场测试

- 至少两种模板能够渲染。
- 模块排序和配置变更有效。
- 非法模块类型无法发布。
- 恶意 HTML 或脚本不能执行。
- 草稿发布不会修改既有历史版本。
- 同一商品在不同专场展示时仍共用库存。
- 专场下线不影响已经创建的订单（**未实现**：无 unpublish 测试）。
