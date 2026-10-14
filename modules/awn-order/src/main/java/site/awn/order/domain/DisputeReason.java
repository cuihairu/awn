package site.awn.order.domain;

/** 争议事由（开单时买家选择，落库快照） */
public enum DisputeReason {
    NOT_DELIVERED,
    TICKET_INVALID,
    DESCRIPTION_MISMATCH,
    OTHER
}
