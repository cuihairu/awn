package site.awn.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.marketplace.api.CreateListingCommand;
import site.awn.marketplace.api.CreateSellerCommand;
import site.awn.marketplace.api.ListingView;
import site.awn.marketplace.api.MarketplaceFacade;
import site.awn.marketplace.api.SubmitSupplyReviewCommand;
import site.awn.marketplace.api.SupplyReviewView;
import site.awn.marketplace.api.TicketUnitView;
import site.awn.marketplace.domain.MarketplaceConflictException;
import site.awn.marketplace.domain.MarketplaceNotFoundException;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 05：卖家资料、供货审核、挂牌；独立票品状态与供货凭证。
 * 验收口径：
 * - 未审核供货不能被标为「平台保证可交付」；已拒绝供货不得挂牌；
 * - 供货凭证仅白名单存储域；
 * - 票品状态与挂牌解耦：挂牌下架 / 票品作废互不隐式改变对方状态。
 */
@SpringBootTest
class MarketplaceModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired MarketplaceFacade marketplace;

    @Test
    void unapprovedSupplyCannotBePlatformGuaranteed() {
        String seller = registerSeller();
        SupplyReviewView pending = submitReview(seller, "https://assets.awn.site/proof.png");

        // 未审核供货 + 平台保证 → 拒绝
        assertThatThrownBy(() -> marketplace.createListing(
                new CreateListingCommand(seller, pending.reviewNo(), null, 8800, true)))
                .isInstanceOf(MarketplaceConflictException.class)
                .hasMessageContaining("platform guarantee requires approved supply review");

        // 未审核供货可挂（卖家自担），但绝不是平台保证
        ListingView risky = marketplace.createListing(
                new CreateListingCommand(seller, pending.reviewNo(), null, 8800, false));
        assertThat(risky.platformGuaranteed()).isFalse();
        assertThat(risky.status()).isEqualTo("LISTED");

        // 已拒绝供货不得挂牌
        SupplyReviewView rejected = submitReview(seller, null);
        marketplace.rejectSupplyReview(rejected.reviewNo(), "审核员-1", "凭证不完整");
        assertThatThrownBy(() -> marketplace.createListing(
                new CreateListingCommand(seller, rejected.reviewNo(), null, 8800, false)))
                .isInstanceOf(MarketplaceConflictException.class)
                .hasMessageContaining("rejected supply review cannot be listed");

        // 审核通过后平台保证才可用
        SupplyReviewView approved = submitReview(seller, "https://assets.awn.site/proof2.png");
        marketplace.approveSupplyReview(approved.reviewNo(), "审核员-1");
        ListingView guaranteed = marketplace.createListing(
                new CreateListingCommand(seller, approved.reviewNo(), null, 8800, true));
        assertThat(guaranteed.platformGuaranteed()).isTrue();
    }

    @Test
    void platformGuaranteedListingRespectsSupplyQuota() {
        String seller = registerSeller();
        // quantity=2 的审核：额度为 2 张平台保证票
        SupplyReviewView review = marketplace.submitSupplyReview(new SubmitSupplyReviewCommand(
                seller, "prod-" + UUID.randomUUID().toString().substring(0, 8), null,
                2, 6800, null));
        marketplace.approveSupplyReview(review.reviewNo(), "审核员-1");

        // 额度内：前两张平台保证挂牌成功
        marketplace.createListing(new CreateListingCommand(seller, review.reviewNo(), null, 6800, true));
        marketplace.createListing(new CreateListingCommand(seller, review.reviewNo(), null, 6800, true));

        // 额度耗尽：第三张平台保证挂牌被拒（一次 APPROVED 不得挂任意多张）
        assertThatThrownBy(() -> marketplace.createListing(
                new CreateListingCommand(seller, review.reviewNo(), null, 6800, true)))
                .isInstanceOf(MarketplaceConflictException.class)
                .hasMessageContaining("supply quota exhausted");

        // 卖家自担挂牌不消耗审核额度：额度耗尽后仍可挂非保证票
        ListingView risky = marketplace.createListing(
                new CreateListingCommand(seller, review.reviewNo(), null, 6800, false));
        assertThat(risky.platformGuaranteed()).isFalse();
        assertThat(risky.status()).isEqualTo("LISTED");
    }

    @Test
    void supplyReviewLifecycleGuards() {
        String seller = registerSeller();
        SupplyReviewView review = submitReview(seller, "https://cdn.awn.site/voucher.pdf");
        assertThat(review.status()).isEqualTo("PENDING");

        SupplyReviewView approved = marketplace.approveSupplyReview(review.reviewNo(), "审核员-2");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.reviewer()).isEqualTo("审核员-2");

        // 已审核的申请不得再次裁决
        assertThatThrownBy(() -> marketplace.rejectSupplyReview(review.reviewNo(), "审核员-3", "late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already decided");
    }

    @Test
    void supplyEvidenceMustUseWhitelistedStorageDomain() {
        String seller = registerSeller();

        // http 与外域一律拒绝
        assertThatThrownBy(() -> submitReview(seller, "http://assets.awn.site/proof.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whitelisted storage domain");
        assertThatThrownBy(() -> submitReview(seller, "https://evil.example.com/proof.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whitelisted storage domain");

        // 白名单域与平台解析的相对路径可用
        assertThat(submitReview(seller, "https://img.awn.site/p.png").reviewNo()).isNotBlank();
        assertThat(submitReview(seller, "/storage/voucher/1.pdf").reviewNo()).isNotBlank();
    }

    @Test
    void suspendedSellerCannotSubmitOrList() {
        String seller = registerSeller();
        SupplyReviewView review = submitReview(seller, null);
        marketplace.suspendSeller(seller);

        // 停用卖家不得再提交供货审核或挂牌
        assertThatThrownBy(() -> submitReview(seller, null))
                .isInstanceOf(MarketplaceConflictException.class)
                .hasMessageContaining("suspended seller cannot submit supply review");
        assertThatThrownBy(() -> marketplace.createListing(
                new CreateListingCommand(seller, review.reviewNo(), null, 8800, false)))
                .isInstanceOf(MarketplaceConflictException.class)
                .hasMessageContaining("suspended seller cannot create listing");
    }

    @Test
    void ticketUnitStatusIsDecoupledFromListing() {
        String seller = registerSeller();
        SupplyReviewView review = submitReview(seller, null);
        marketplace.approveSupplyReview(review.reviewNo(), "审核员-1");
        ListingView listing = marketplace.createListing(
                new CreateListingCommand(seller, review.reviewNo(), null, 6800, true));
        TicketUnitView unit = marketplace.unitOfListing(listing.listingNo());
        assertThat(unit.status()).isEqualTo("IN_STOCK");

        // 挂牌下架不隐式改票品状态（票品留在库中）
        ListingView delisted = marketplace.delist(listing.listingNo());
        assertThat(delisted.status()).isEqualTo("DELISTED");
        assertThat(marketplace.unitOfListing(listing.listingNo()).status()).isEqualTo("IN_STOCK");
    }

    @Test
    void invalidUnitTransitionIsRejected() {
        String seller = registerSeller();
        SupplyReviewView review = submitReview(seller, null);
        marketplace.approveSupplyReview(review.reviewNo(), "审核员-1");
        ListingView listing = marketplace.createListing(
                new CreateListingCommand(seller, review.reviewNo(), null, 6800, false));
        TicketUnitView unit = marketplace.unitOfListing(listing.listingNo());

        // 票品作废：IN_STOCK → INVALID
        TicketUnitView invalid = marketplace.markUnitInvalid(unit.unitNo());
        assertThat(invalid.status()).isEqualTo("INVALID");

        // 非法迁移：INVALID 不可再作废
        assertThatThrownBy(() -> marketplace.markUnitInvalid(unit.unitNo()))
                .isInstanceOf(MarketplaceConflictException.class)
                .hasMessageContaining("not allowed");
        // 票品作废不隐式改挂牌状态
        assertThat(marketplace.listingOf(listing.listingNo()).status()).isEqualTo("LISTED");
    }

    @Test
    void unknownReferencesAreNotFound() {
        assertThatThrownBy(() -> marketplace.sellerOf("SEL-none"))
                .isInstanceOf(MarketplaceNotFoundException.class);
        assertThatThrownBy(() -> marketplace.listingOf("LST-none"))
                .isInstanceOf(MarketplaceNotFoundException.class);
        assertThatThrownBy(() -> marketplace.markUnitInvalid("TKT-none"))
                .isInstanceOf(MarketplaceNotFoundException.class);
    }

    // ------------------------------------------------------------------

    private String registerSeller() {
        return marketplace.registerSeller(new CreateSellerCommand(
                (long) (100 + UUID.randomUUID().toString().charAt(0) % 500),
                "测试卖家-" + UUID.randomUUID(), null))
                .sellerNo();
    }

    private SupplyReviewView submitReview(String sellerNo, String evidenceUrl) {
        return marketplace.submitSupplyReview(new SubmitSupplyReviewCommand(
                sellerNo, "prod-" + UUID.randomUUID().toString().substring(0, 8), null,
                1, 6800, evidenceUrl));
    }
}
