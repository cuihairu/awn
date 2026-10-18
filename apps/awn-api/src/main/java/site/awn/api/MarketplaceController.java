package site.awn.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import site.awn.api.auth.AuthenticatedUser;
import site.awn.marketplace.api.ListingView;
import site.awn.marketplace.api.MarketplaceFacade;

/**
 * 挂牌卖家面（组合根，docs/architecture/dispute-handling.md「卖家面」）：
 * 卖家取消挂牌要求 bearer token 与 sellerNo 归属一致，再由市场模块校验挂牌归属与状态。
 * 卖家与买家一样没有受理或裁决入口（裁决只在 /api/v1/admin/**）。
 */
@RestController
public class MarketplaceController {

    private final MarketplaceFacade marketplace;

    public MarketplaceController(MarketplaceFacade marketplace) {
        this.marketplace = marketplace;
    }

    /** 卖家取消挂牌：LISTED → DELISTED；RESERVED / SOLD / 已 DELISTED 不可撤（409）。 */
    @PostMapping("/api/v1/listings/{listingNo}/delist")
    public ListingView delist(@PathVariable String listingNo, @RequestBody DelistListingRequest request,
        HttpServletRequest httpRequest) {
        long userId = AuthenticatedUser.userId(httpRequest);
        String sellerNo = request.sellerNo();
        AuthenticatedUser.requireOwner(marketplace.sellerOf(sellerNo).userId(), userId);
        return marketplace.delist(listingNo, sellerNo);
    }

    public record DelistListingRequest(String sellerNo) {
    }
}
