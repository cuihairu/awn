/**
 * 采集、标准化、价格历史和分析。
 *
 * <p>该模块<strong>不允许</strong>：写入交易库存、订单和支付表。</p>
 *
 * <p>内部结构约定：api（对外接口）、application（用例）、
 * domain（领域模型）、infrastructure（持久化实现）。
 * 其他模块只允许依赖本模块 api 包中有意公开的接口。</p>
 *
 * @see <a href="https://docs.awn.site/architecture/module-boundaries">模块边界</a>
 */
package site.awn.intelligence;
