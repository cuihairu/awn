package site.awn.intelligence.domain;

import java.time.Instant;

/** 来源适配器产出的一条原始观察草稿 */
public record ObservationDraft(String externalRef, String productRef,
                               Instant observedAt, Long price, String currency,
                               String availabilityLabel, String rawPayload) {
}
