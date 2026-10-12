package site.awn.intelligence.api;

import site.awn.intelligence.domain.CollectReport;

import java.util.List;

/**
 * 票务情报对外接口：来源注册、采集、价格历史查询。
 * 隔离约束：情报只读外部世界与自己的 schema，不写交易表；
 * 采集失败不影响交易（CollectReport.error 携带失败原因）。
 */
public interface IntelligenceFacade {

    SourceView registerSource(CreateSourceCommand command);

    SourceView sourceOf(String sourceNo);

    /** 对指定来源执行一轮采集：原始落库 → 标准化 → 质量检查 → 价格历史 */
    CollectReport collect(String sourceNo);

    /** 价格历史（可溯源：来源、更新时间、可信度） */
    List<PricePointView> priceHistoryOf(String productCode);
}
