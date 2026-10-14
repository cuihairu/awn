/**
 * 图鉴领域模型：人物、作品、人物参与作品的关系，以及领域异常。
 *
 * <p>领域异常是模块对外契约的一部分：组合根需要据此映射 HTTP 语义
 * （docs/api/contracts.md 错误码），因此本包显式暴露。</p>
 */
@org.springframework.modulith.NamedInterface("star-domain")
package site.awn.star.domain;