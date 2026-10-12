package site.awn.marketplace.api;

/**
 * 卖家市场对外接口：卖家资料、供货审核、挂牌。
 * 平台边界：未审核供货不能被标为「平台保证可交付」。
 */
public interface MarketplaceFacade {

    SellerView registerSeller(CreateSellerCommand command);

    SellerView sellerOf(String sellerNo);

    /** 停用卖家：停用后不得提交供货审核或挂牌 */
    SellerView suspendSeller(String sellerNo);

    /** 提交供货审核（evidenceUrl 仅允许白名单存储域） */
    SupplyReviewView submitSupplyReview(SubmitSupplyReviewCommand command);

    SupplyReviewView approveSupplyReview(String reviewNo, String reviewer);

    SupplyReviewView rejectSupplyReview(String reviewNo, String reviewer, String reason);

    /** 挂牌：platformGuaranteed=true 要求该供货审核已通过 */
    ListingView createListing(CreateListingCommand command);

    ListingView listingOf(String listingNo);

    ListingView delist(String listingNo);

    TicketUnitView unitOf(String unitNo);

    /** 挂牌对应的独立票品（1:1） */
    TicketUnitView unitOfListing(String listingNo);

    /** 票品状态独立流转（与挂牌解耦）：用于作废等卖家侧操作 */
    TicketUnitView markUnitInvalid(String unitNo);
}
