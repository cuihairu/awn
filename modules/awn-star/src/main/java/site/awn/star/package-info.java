/**
 * 明星图鉴：人物、作品与人物参与作品的关系。
 *
 * <p>该模块<strong>不允许</strong>：修改活动、商品、订单、库存、支付等任何交易数据；
 * 图鉴是编辑型内容数据，与交易链路零耦合。照片只允许有授权来源的图片，
 * 缺图一律占位，不生成人脸（素材红线见 docs/design/star-catalog.md §3）。</p>
 *
 * <p>内部结构约定：api（对外接口）、application（用例）、
 * domain（领域模型）、infrastructure（持久化实现）。
 * 其他模块只允许依赖本模块 api 包中有意公开的接口。</p>
 *
 * @see <a href="https://docs.awn.site/architecture/module-boundaries">模块边界</a>
 */
package site.awn.star;
