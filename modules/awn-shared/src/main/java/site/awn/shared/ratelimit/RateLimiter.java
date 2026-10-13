package site.awn.shared.ratelimit;

import java.time.Duration;

/**
 * 固定窗口限流器：同一 key 在 window 内最多放行 maxRequests 次，超出返回 false。
 * 窗口起点为该 key 计数首次创建时刻，窗口整体过期后额度复原（固定窗口，非滑动窗口）。
 */
public interface RateLimiter {

    /**
     * 申请一次额度。
     *
     * @return true 放行；false 超过上限，调用方应拒绝（HTTP 429）
     */
    boolean allow(String key, int maxRequests, Duration window);
}
