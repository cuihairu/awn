/**
 * 卖家、供货审核、挂牌、独立票品的领域模型。
 *
 * <p>领域异常是模块对外契约的一部分：组合根需要据此映射 HTTP 语义
 * （docs/api/contracts.md 错误码），因此本包显式暴露。</p>
 */
@org.springframework.modulith.NamedInterface("marketplace-domain")
package site.awn.marketplace.domain;
