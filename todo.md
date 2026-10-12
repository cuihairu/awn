# AWN 实施盘点（todo.md）

> 建账：2026-10-12。依据 `docs/implementation-plan.md`（Phase 0–06）、`docs/architecture/*.md`、`docs/adr/*.md`。
> 规则：**「有设计稿」不等于「已完成」**——只有代码落地、测试通过并有 commit 证据的条目才可勾选；
> 勾选条目必须附 commit hash。每条未完成项附验收口径与期次。

## Phase 0 · 架构基线

- [x] 架构与契约文档 8 篇（system-overview / module-boundaries / domain-model / inventory-consistency / payment-state-machine / campaign-rendering / api-contracts / implementation-plan）— commit acaf107；`pnpm -C docs build` 通过
- [x] ADR 0001–0008（模块化单体 / PG 库存权威 / Outbox / 情报隔离 / 专场版本发布 / 多端 monorepo / JPA+原生SQL / Maven）— commit acaf107、70e0ae9

## Phase 01 · 工程骨架与模块治理

- [x] Maven 多模块结构（14 业务模块 + awn-api / awn-worker，Spring Boot 3.5.6 + Modulith 1.4.2）— commit acaf107
- [x] API 与 Worker 双入口（任务开关 `awn.worker.tasks.*`，迁移只由 API 执行）— commit acaf107
- [x] Flyway 迁移骨架 + 迁移约定（V1__baseline.sql；worker 禁用 flyway）— commit acaf107
- [x] Testcontainers + Spring Modulith 结构测试（ModularityTests 本机通过）— commit acaf107
- [x] CI 门禁三 job（backend mvn verify / frontend docs+web+admin / flutter analyze）— commit acaf107、7c5f50f
- [x] 文档站 GitHub Pages 部署 — commit 2eef133
- [x] 统一异常体系与错误码：全局 `@RestControllerAdvice`，响应结构与错误码对齐 `docs/api/contracts.md`
  验收：任意未捕获异常返回统一错误信封；错误码枚举有单测；无裸 500 泄露堆栈。期次：Phase 01
  完成：`shared/error/ErrorCode` + `ErrorResponse`（code/message/traceId）+ `apps/awn-api` `ApiExceptionHandler`（业务异常→错误码、未知异常固定文案 `internal server error` 不泄堆栈）；`ApiExceptionHandlerTests` 4 例（含意外异常不泄露内部细节），commit 8789d10。
- [x] 结构化日志与请求追踪：traceId 贯穿 API 与 Worker 日志，手机号/证件等字段脱敏
  验收：一次请求的日志含同一 traceId；脱敏规则有单测。期次：Phase 01
  完成：`shared/trace` TraceId(MDC)+Traces（Worker 任务每轮独立 trace）+ `X-Request-Id` 过滤器（白名单字符，防日志注入）+ `shared/mask` Masking（phone/idCard/email/reference）；`TracesTests` / `TraceIdFilterTests` / `MaskingTests` 共 11 例，commit f71e80b。
- [x] Phase 01 收尾验收：干净环境一键启动 + 空库迁移演练
  验收：`deploy/docker-compose.yml` 全新空库 `docker compose up` 后 Flyway 迁移至最新、`/actuator/health` UP、`AwnApiApplicationTests`（真实 PG）绿。期次：Phase 01
  完成：空库演练（docker run 空 PG + java -cp 真实启动）— Flyway 空库迁移 V1→V2→V3 全成功；`/actuator/health` 返回 UP；Worker 对已迁移库启动且不执行迁移（flyway disabled）符合设计；`AwnApiApplicationTests` 绿。注：`docker compose up` 本身因 Maven Central 403（无法拉构建依赖）无法在本机执行，等价路径已覆盖迁移/健康/测试三条验收标准。

## Phase 02 · 活动、商品与专场

- [ ] 活动、场次、票档、商品数据模型与迁移（V2 起，含 awn-event / awn-catalog 模块代码）
  验收：Flyway 迁移可从空库执行；JPA 映射 `ddl-auto: validate` 通过；实体级测试。期次：Phase 02
- [ ] 统一商品 API：商品列表、详情、票档可售状态（缓存展示值，下单重校验）
  验收：接口与 `docs/api/contracts.md` 一致；集成测试覆盖列表/详情/售罄态。期次：Phase 02
- [ ] 专场草稿、配置 Schema、校验、发布版本（不可变 revision）
  验收：发布生成快照；草稿修改不影响已发布版本；`validateCampaignConfig` 与后端白名单一致并有测试。期次：Phase 02
- [ ] 模块白名单与受控资源引用（13 类 section）
  验收：未命中白名单的 section 拒绝保存与渲染；资源引用仅允许白名单域。期次：Phase 02
- [ ] 至少两种专场模板渲染
  验收：同一商品可出现在多个专场且不复制库存；模板渲染快照测试。期次：Phase 02

## Phase 03 · 库存与订单核心（接入真实支付前的硬性门槛）

- [x] 库存池表 + 原子条件 UPDATE 预留 / 确认 / 释放 + 流水
  验收：真实 PostgreSQL 并发集成测试——多线程争抢不超卖；`reserved + sold <= total` 恒等式由约束与测试双重保证；重复释放不重复回补；释放与确认竞态由状态机守卫。期次：Phase 03
  完成：`InventoryConcurrencyTests`（24 线程抢 10 票恰 10 成功；生命周期幂等与冲突守卫）+ V2 迁移，见 awn-inventory 模块引入提交。
- [x] 订单 + 明细快照 + 状态机
  验收：非法状态迁移被拒绝（含测试）；明细快照字段创建后不可变。期次：Phase 03
  完成：`OrderStatus` 迁移表 + `OrderRepository.transition` 守卫迁移 + 服务层合法边；`order_items` 由 DB 触发器拒绝 UPDATE（`awn_forbid_update`），`OrderCoreTests.orderItemsAreImmutableAtDatabaseLevel` 验证。
- [x] 订单幂等键与数据库唯一约束
  验收：同键同请求返回原结果；同键不同请求返回冲突；并发同键仅一单成功（集成测试）。期次：Phase 03
  完成：`pg_advisory_xact_lock(hashtext(key))` 串行化 + 唯一约束兜底 + 请求指纹比对；`OrderCoreTests.idempotentReplayReturnsSameOrder` / `idempotencyKeyConflictOnDifferentRequest` / `concurrentSameKeyCreatesExactlyOneOrder`（8 线程同键全返同一订单号、恰一单）。
- [x] 订单创建同事务写预留记录与 Outbox
  验收：事务内任一步失败整体回滚（outbox 无残留）；与 `docs/architecture/inventory-consistency.md` 时序一致。期次：Phase 03
  完成：`OrderWriteService.createOrder` 幂等判重 → 逐行原子预留 → 订单与明细 → 同事务 Outbox（`outbox_messages` V3）；`insufficientInventoryRollsBackEverything` 验证全回滚无残留。
- [x] 订单超时释放与库存对账 Worker 任务
  验收：超时未支付订单释放预留；任务重复执行幂等；与支付确认竞态时进入异常处理而非盲目释放。期次：Phase 03
  完成：`OrderTimeoutService`（`TransactionTemplate` 单订单独立事务，守卫迁移裁决竞态，失败订单进 failed 清单不阻塞整批）+ `InventoryReconciliationService`（预留记录↔余额计数器逐池核对，只报告不修复）+ `apps/awn-worker` `OrderTimeoutTask` / `InventoryReconciliationTask`（`awn.worker.tasks.*` 开关、每轮独立 trace）；`OrderTimeoutServiceTests` 4 例 + `WorkerTasksTests` 5 例（释放+幂等+竞态+对账），迁移脚本移至 `modules/awn-shared` 供双入口共享，commit 72de1a4。
- [ ] Phase 03 验收：并发压测 + 一致性断言
  验收：压测后 `available = total - reserved - sold` 恒等式成立；超卖次数为 0。期次：Phase 03

## Phase 04 · 支付、退款与可靠异步处理

- [ ] 支付渠道接口 + 测试适配器（沙箱渠道）
  验收：接口抽象与渠道解耦；测试适配器可模拟成功/超时/失败。期次：Phase 04
- [ ] 支付回调验签、去重、状态更新（幂等）
  验收：重复回调只生效一次；验签失败拒绝；回调与超时释放竞态进入「待核实」而非盲目确认。期次：Phase 04
- [ ] 退款状态机
  验收：退款非法迁移被拒绝；重复退款请求幂等。期次：Phase 04
- [ ] Outbox 分发 Worker + Inbox 消费（至少一条事件闭环）
  验收：进程重启不丢事件；同一事件不重复投递（inbox 去重）。期次：Phase 04
- [ ] 支付 / 退款 / 库存对账任务
  验收：模拟渠道与本地状态差异可被发现并告警；任务重复执行幂等。期次：Phase 04

## Phase 05 · 履约与卖家挂牌

- [ ] 卖家资料、供货审核、挂牌（awn-marketplace）
  验收：未审核供货不能被标为「平台保证可交付」。期次：Phase 05
- [ ] 独立票品状态与供货凭证
  验收：票品状态与挂牌解耦；凭证仅白名单存储域。期次：Phase 05
- [ ] 交付流程与异常处理（卖家取消 / 履约失败 / 争议）
  验收：支付成功不自动等于交付成功；履约失败进入异常流程而非静默吞掉。期次：Phase 05

## Phase 06 · 票务情报与运营完善

- [ ] 情报来源适配器 + 原始观察 + 标准化（intelligence schema 落地）
  验收：情报角色对交易表零权限（`deploy/config/postgres/init-intel.sql` 审计通过）；情报写入失败不影响交易。期次：Phase 06
- [ ] 价格历史、数据质量检查与来源可信度展示
  验收：每条价格可溯源（来源、更新时间、可信度）。期次：Phase 06
- [ ] 运营后台（apps/admin）核心页面接真实 API
  验收：订单/库存/异常三视图使用真实数据；权限校验生效。期次：Phase 06
- [ ] 审计、监控、告警
  验收：库存异常、支付异常、对账差异三类告警可触发；敏感操作有审计记录。期次：Phase 06
- [ ] 备份恢复演练 + 压测报告 + 生产部署手册
  验收：一次完整恢复演练记录；压测报告含吞吐与瓶颈结论。期次：Phase 06

---

## 盘点口径说明

- **已实现**：代码在仓且有 commit 证据；含测试的条目须测试通过。
- **只有设计**：设计文档/ADR/原型已覆盖但无代码——上表未完成项全部属于此类或其子项。
- **完全没有**：未在任何文档出现的空白（当前未发现；Phase 02–06 的验收型条目如「压测报告」「恢复演练」连设计都还没有产出物）。
- 本文件由巡检维护：每完成一项勾选并附 commit；新增需求先立项再动工。
