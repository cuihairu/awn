/**
 * 订单领域模型、状态机与领域异常。
 *
 * <p>领域异常是模块对外契约的一部分：组合根需要据此映射 HTTP 语义
 * （docs/api/contracts.md 错误码），因此本包显式暴露。
 */
@org.springframework.modulith.NamedInterface("order-domain")
package site.awn.order.domain;
