package site.awn.order.api;

import site.awn.order.domain.DisputeReason;
import site.awn.order.domain.DisputeResolution;
import site.awn.order.domain.DisputeRequestType;
import site.awn.order.domain.DisputeStatus;

import java.time.Instant;

/** 争议单视图。sellerId / listingNo / unitNo 为开单时的卖家与凭证行快照，可为 null。 */
public record DisputeView(
        String disputeNo,
        String orderNo,
        Long openedBy,
        Long sellerId,
        String listingNo,
        String unitNo,
        DisputeReason reason,
        String description,
        String evidenceUrl,
        DisputeRequestType requestedResolution,
        Long requestedAmount,
        DisputeStatus status,
        DisputeResolution resolution,
        String resolutionNote,
        String decidedBy,
        Instant decidedAt,
        Instant createdAt) {
}
