package site.awn.intelligence.api;

import site.awn.intelligence.domain.CollectReport;

import java.util.List;

/** 一轮全量采集汇总：轮到的启用来源数、失败来源数、逐源报告 */
public record CollectAllReport(int checked, int failedCount, List<CollectReport> reports) {
}
