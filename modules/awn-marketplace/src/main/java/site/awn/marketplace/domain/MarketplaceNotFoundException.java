package site.awn.marketplace.domain;

/** 卖家 / 供货审核 / 挂牌 / 票品不存在 */
public class MarketplaceNotFoundException extends RuntimeException {

    public MarketplaceNotFoundException(String message) {
        super(message);
    }
}
