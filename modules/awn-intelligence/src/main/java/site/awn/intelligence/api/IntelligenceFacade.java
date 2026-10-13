package site.awn.intelligence.api;

import site.awn.intelligence.domain.CollectReport;

import java.util.Collection;
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

    /** 全量采集（调度器调用）：逐个启用来源执行一轮采集，单源失败不中断整轮 */
    CollectAllReport collectAll();

    /** 停用来源：调度采集跳过停用来源；手动单源采集不受限 */
    SourceView disableSource(String sourceNo);

    /** 恢复来源：重新参与调度采集（与停用对称） */
    SourceView enableSource(String sourceNo);

    /** 价格历史（可溯源：来源、更新时间、可信度） */
    List<PricePointView> priceHistoryOf(String productCode);

    /** 场次集合的价格历史（活动维度价格走势），按观测时间倒序 */
    List<PricePointView> priceHistoryOfSessions(Collection<String> sessionCodes);
}
