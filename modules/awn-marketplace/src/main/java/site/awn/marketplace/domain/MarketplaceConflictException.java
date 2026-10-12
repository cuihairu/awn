package site.awn.marketplace.domain;

/** 挂牌 / 票品 / 供货审核的业务冲突（非法状态迁移、资质不足等） */
public class MarketplaceConflictException extends RuntimeException {

    public MarketplaceConflictException(String message) {
        super(message);
    }
}
