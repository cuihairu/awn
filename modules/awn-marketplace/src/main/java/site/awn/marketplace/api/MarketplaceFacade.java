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

    /**
     * 为订单预留挂牌票品（凭证型订单行，docs/architecture/trading-model.md G2）：
     * 挂牌 LISTED → RESERVED，票品 IN_STOCK → RESERVED，与订单同事务提交。
     * 并发抢单时守卫迁移只放行一单，其余抛
     * {@link site.awn.marketplace.domain.MarketplaceConflictException}（调用方整体回滚）。
     *
     * @return 被预留的票品号（订单行须落库此号，供取消 / 支付确认按行操作）
     */
    String reserveUnitForOrder(String listingNo);

    /**
     * 释放预留票品（订单取消 / 超时）：RESERVED → IN_STOCK，挂牌 RESERVED → LISTED。
     *
     * @return true 表示本次完成迁移，false 表示幂等重放（票品已不在 RESERVED）
     */
    boolean releaseUnitForOrder(String unitNo);

    /**
     * 确认预留票品成交（支付确认）：RESERVED → SOLD，挂牌 RESERVED → SOLD。
     *
     * @return true 表示本次完成迁移，false 表示幂等重放（票品已不在 RESERVED）
     */
    boolean confirmUnitForOrder(String unitNo);
}
