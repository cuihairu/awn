package site.awn.intelligence.domain;

import java.util.List;

/**
 * 情报来源适配器：把外部来源的页面 / 接口响应转换为原始观察草稿。
 * 适配器只读外部数据，禁止触碰交易表。
 */
public interface IntelligenceAdapter {

    /** 适配器绑定的来源编号 */
    String sourceNo();

    /** 拉取一批观察草稿；实现方自行处理外部超时与重试 */
    List<ObservationDraft> fetch();
}
