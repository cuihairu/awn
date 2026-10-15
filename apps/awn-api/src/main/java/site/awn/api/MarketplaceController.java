package site.awn.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import site.awn.marketplace.api.ListingView;
import site.awn.marketplace.api.MarketplaceFacade;

/**
 * 挂牌卖家面（组合根，docs/architecture/dispute-handling.md「卖家面」）：
 * 卖家取消挂牌必须声明归属（sellerNo），服务端比对挂牌卖家，不符一律 409。
 * 交易面当前无认证中间件：声明身份 + 服务端归属校验，认证接入后守卫逻辑不变。
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
    public ListingView delist(@PathVariable String listingNo, @RequestBody DelistListingRequest request) {
        return marketplace.delist(listingNo, request.sellerNo());
    }

    public record DelistListingRequest(String sellerNo) {
    }
}
