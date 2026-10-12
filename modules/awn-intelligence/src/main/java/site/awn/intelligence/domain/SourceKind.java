package site.awn.intelligence.domain;

/**
 * 情报来源数据分类（docs/architecture/intelligence-isolation.md）。
 * 分类不允许混用：外部页面显示「还有 10 张」不能转化为 AWN 库存中的 10 张。
 */
public enum SourceKind {
    /** 官方公布票价 */
    OFFICIAL_PUBLISHED,
    /** 第三方公开标价 */
    THIRD_PARTY_LISTED,
    /** 历史采集价格 */
    HISTORICAL_COLLECTED,
    /** 外部页面显示的余票状态 */
    EXTERNAL_AVAILABILITY
}
