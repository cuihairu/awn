package site.awn.intelligence.domain;

/** 一轮采集报告：原始条数、标准化通过数、拒绝数；error 非空表示采集失败（不影响交易） */
public record CollectReport(String sourceNo, int rawCount, int okCount, int rejectedCount, String error) {

    public boolean failed() {
        return error != null;
    }
}
