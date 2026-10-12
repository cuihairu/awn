package site.awn.intelligence.api;

import java.time.Instant;

/** 价格点视图：每条价格可溯源（来源、更新时间、可信度） */
public record PricePointView(long price, String currency, String availability,
                             String sourceNo, String sourceName, String kind,
                             int trustTier, Instant observedAt) {
}
