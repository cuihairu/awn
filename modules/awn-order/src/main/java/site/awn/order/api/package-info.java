/**
 * 订单模块对外接口：其他模块只允许依赖本包，
 * domain / infrastructure / application 均为模块内部。
 * 领域异常单独暴露于 order-domain（组合根映射 HTTP 语义）。
 */
@org.springframework.modulith.NamedInterface("order-api")
package site.awn.order.api;
