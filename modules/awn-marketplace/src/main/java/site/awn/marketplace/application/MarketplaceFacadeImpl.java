package site.awn.marketplace.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.marketplace.api.CreateListingCommand;
import site.awn.marketplace.api.CreateSellerCommand;
import site.awn.marketplace.api.ListingView;
import site.awn.marketplace.api.MarketplaceFacade;
import site.awn.marketplace.api.SellerView;
import site.awn.marketplace.api.SubmitSupplyReviewCommand;
import site.awn.marketplace.api.SupplyReviewView;
import site.awn.marketplace.api.TicketUnitView;
import site.awn.marketplace.domain.Listing;
import site.awn.marketplace.domain.MarketplaceConflictException;
import site.awn.marketplace.domain.MarketplaceNotFoundException;
import site.awn.marketplace.domain.Seller;
import site.awn.marketplace.domain.SupplyReview;
import site.awn.marketplace.domain.SupplyReviewStatus;
import site.awn.marketplace.domain.TicketUnit;
import site.awn.marketplace.infrastructure.ListingRepository;
import site.awn.marketplace.infrastructure.SellerRepository;
import site.awn.marketplace.infrastructure.SupplyReviewRepository;
import site.awn.marketplace.infrastructure.TicketUnitRepository;
import site.awn.audit.api.AuditFacade;
import site.awn.audit.api.RecordAuditCommand;
import site.awn.shared.url.StorageUrlValidator;

import java.util.UUID;

/**
 * 卖家市场用例实现。
 * 供货凭证仅允许白名单存储域；「平台保证可交付」只能来自已通过的供货审核。
 */
@Service
public class MarketplaceFacadeImpl implements MarketplaceFacade {

    private final SellerRepository sellers;
    private final SupplyReviewRepository reviews;
    private final ListingRepository listings;
    private final TicketUnitRepository units;
    private final AuditFacade audit;

    public MarketplaceFacadeImpl(SellerRepository sellers, SupplyReviewRepository reviews,
                                 ListingRepository listings, TicketUnitRepository units,
                                 AuditFacade audit) {
        this.sellers = sellers;
        this.reviews = reviews;
        this.listings = listings;
        this.units = units;
        this.audit = audit;
    }

    @Override
    @Transactional
    public SellerView registerSeller(CreateSellerCommand command) {
        if (command.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        Seller seller = new Seller("SEL-" + UUID.randomUUID(),
                command.userId(), command.displayName(), command.contact());
        return toView(sellers.save(seller));
    }

    @Override
    @Transactional(readOnly = true)
    public SellerView sellerOf(String sellerNo) {
        return sellers.findBySellerNo(sellerNo).map(this::toView)
                .orElseThrow(() -> new MarketplaceNotFoundException("seller not found: " + sellerNo));
    }

    @Override
    @Transactional
    public SellerView suspendSeller(String sellerNo) {
        Seller seller = sellers.findBySellerNo(sellerNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("seller not found: " + sellerNo));
        seller.suspend();
        // 停用卖家影响其在售挂牌的信任语义：敏感操作留痕
        audit.record(new RecordAuditCommand("platform", "SELLER_SUSPENDED", "seller",
                sellerNo, "{\"displayName\":\"" + seller.getDisplayName() + "\"}"));
        return toView(seller);
    }

    @Override
    @Transactional
    public SupplyReviewView submitSupplyReview(SubmitSupplyReviewCommand command) {
        if (command.quantity() <= 0 || command.unitPrice() <= 0) {
            throw new IllegalArgumentException("quantity and unit price must be positive");
        }
        if (command.evidenceUrl() != null) {
            // 供货凭证仅白名单存储域
            StorageUrlValidator.validate(command.evidenceUrl());
        }
        Seller seller = sellers.findBySellerNo(command.sellerNo())
                .orElseThrow(() -> new MarketplaceNotFoundException("seller not found: " + command.sellerNo()));
        if (!seller.isActive()) {
            throw new MarketplaceConflictException("suspended seller cannot submit supply review: " + command.sellerNo());
        }
        SupplyReview review = new SupplyReview("SRV-" + UUID.randomUUID(), seller.getId(),
                command.productCode(), command.sessionCode(), command.quantity(),
                command.unitPrice(), command.evidenceUrl());
        return toView(reviews.save(review), seller);
    }

    @Override
    @Transactional
    public SupplyReviewView approveSupplyReview(String reviewNo, String reviewer) {
        SupplyReview review = reviewOf(reviewNo);
        review.approve(reviewer);
        // 审核决策是敏感操作：授予「平台保证」资质，必须留痕
        audit.record(new RecordAuditCommand(reviewer, "SUPPLY_REVIEW_APPROVED", "supply_review",
                reviewNo, "{\"productCode\":\"" + review.getProductCode() + "\"}"));
        return toView(review, sellers.findById(review.getSellerId()).orElseThrow());
    }

    @Override
    @Transactional
    public SupplyReviewView rejectSupplyReview(String reviewNo, String reviewer, String reason) {
        SupplyReview review = reviewOf(reviewNo);
        review.reject(reviewer, reason);
        audit.record(new RecordAuditCommand(reviewer, "SUPPLY_REVIEW_REJECTED", "supply_review",
                reviewNo, "{\"reason\":\"" + (reason == null ? "" : reason.replace("\"", "'")) + "\"}"));
        return toView(review, sellers.findById(review.getSellerId()).orElseThrow());
    }

    @Override
    @Transactional
    public ListingView createListing(CreateListingCommand command) {
        Seller seller = sellers.findBySellerNo(command.sellerNo())
                .orElseThrow(() -> new MarketplaceNotFoundException("seller not found: " + command.sellerNo()));
        if (!seller.isActive()) {
            throw new MarketplaceConflictException("suspended seller cannot create listing: " + command.sellerNo());
        }
        SupplyReview review = reviewOf(command.reviewNo());
        if (!review.getSellerId().equals(seller.getId())) {
            throw new MarketplaceConflictException("supply review belongs to another seller: " + command.reviewNo());
        }
        // 平台边界：platform_guaranteed 只能来自 APPROVED 审核；REJECTED 不得挂牌（领域工厂裁决）
        Listing listing = Listing.create("LST-" + UUID.randomUUID(), seller.getId(), review,
                review.getProductCode(), command.sessionCode(), command.unitPrice(),
                command.platformGuaranteed());
        // 额度守卫：platform_guaranteed 挂牌消耗审核额度，一次 APPROVED 不得挂任意多张
        if (command.platformGuaranteed()) {
            int consumed = reviews.consumeQuota(review.getId(), SupplyReviewStatus.APPROVED);
            if (consumed == 0) {
                throw new MarketplaceConflictException(
                        "supply quota exhausted for review: " + command.reviewNo());
            }
        }
        Listing saved = listings.save(listing);
        // 挂牌对应一张独立票品（1:1），票品状态独立流转
        units.save(new TicketUnit("TKT-" + UUID.randomUUID(), saved.getId(), seller.getId()));
        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ListingView listingOf(String listingNo) {
        return listings.findByListingNo(listingNo).map(this::toView)
                .orElseThrow(() -> new MarketplaceNotFoundException("listing not found: " + listingNo));
    }

    @Override
    @Transactional(readOnly = true)
    public Long sellerIdOf(String sellerNo) {
        return sellers.findBySellerNo(sellerNo).map(Seller::getId)
                .orElseThrow(() -> new MarketplaceNotFoundException("seller not found: " + sellerNo));
    }

    @Override
    @Transactional
    public ListingView delist(String listingNo, String sellerNo) {
        Listing listing = listings.findByListingNo(listingNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("listing not found: " + listingNo));
        // 归属校验：调用方声明 sellerNo，比对挂牌卖家，不符一律冲突
        Long sellerId = sellers.findBySellerNo(sellerNo).map(Seller::getId)
                .orElseThrow(() -> new MarketplaceNotFoundException("seller not found: " + sellerNo));
        if (!listing.getSellerId().equals(sellerId)) {
            throw new MarketplaceConflictException("listing does not belong to seller: " + listingNo);
        }
        listing.delist();
        return toView(listing);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketUnitView unitOf(String unitNo) {
        return units.findByUnitNo(unitNo).map(this::toView)
                .orElseThrow(() -> new MarketplaceNotFoundException("ticket unit not found: " + unitNo));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketUnitView unitOfListing(String listingNo) {
        Listing listing = listings.findByListingNo(listingNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("listing not found: " + listingNo));
        return units.findByListingId(listing.getId()).map(this::toView)
                .orElseThrow(() -> new MarketplaceNotFoundException("ticket unit not found for listing: " + listingNo));
    }

    @Override
    @Transactional
    public TicketUnitView markUnitInvalid(String unitNo) {
        TicketUnit unit = units.findByUnitNo(unitNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("ticket unit not found: " + unitNo));
        unit.transitionTo(site.awn.marketplace.domain.TicketUnitStatus.INVALID);
        return toView(unit);
    }

    @Override
    @Transactional
    public String reserveUnitForOrder(String listingNo) {
        Listing listing = listings.findByListingNo(listingNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("listing not found: " + listingNo));
        // 两条守卫迁移：挂牌与票品各一条条件 UPDATE，0 行即抢单失败或状态不符
        if (listings.markReservedByListingId(listing.getId()) == 0) {
            throw new MarketplaceConflictException(
                    "listing cannot be reserved: " + listingNo);
        }
        if (units.reserveByListingId(listing.getId()) == 0) {
            throw new MarketplaceConflictException(
                    "ticket unit not in stock for listing: " + listingNo);
        }
        return units.findByListingId(listing.getId()).orElseThrow().getUnitNo();
    }

    @Override
    @Transactional
    public boolean releaseUnitForOrder(String unitNo) {
        TicketUnit unit = units.findByUnitNo(unitNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("ticket unit not found: " + unitNo));
        if (units.releaseByUnitNo(unitNo) == 0) {
            return false; // 幂等重放：票品已不在 RESERVED（已成交或已释放）
        }
        listings.markListedByListingId(unit.getListingId());
        return true;
    }

    @Override
    @Transactional
    public boolean confirmUnitForOrder(String unitNo) {
        TicketUnit unit = units.findByUnitNo(unitNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("ticket unit not found: " + unitNo));
        if (units.confirmByUnitNo(unitNo) == 0) {
            return false; // 幂等重放：票品已不在 RESERVED
        }
        listings.markSoldByListingId(unit.getListingId());
        return true;
    }

    // ------------------------------------------------------------------

    private SupplyReview reviewOf(String reviewNo) {
        return reviews.findByReviewNo(reviewNo)
                .orElseThrow(() -> new MarketplaceNotFoundException("supply review not found: " + reviewNo));
    }

    private SellerView toView(Seller s) {
        return new SellerView(s.getSellerNo(), s.getUserId(), s.getDisplayName(), s.getContact(),
                s.getStatus().name());
    }

    private SupplyReviewView toView(SupplyReview r, Seller seller) {
        return new SupplyReviewView(r.getReviewNo(), seller.getSellerNo(), r.getProductCode(),
                r.getSessionCode(), r.getQuantity(), r.getUnitPrice(), r.getStatus().name(),
                r.getReviewer(), r.getRejectReason());
    }

    private ListingView toView(Listing l) {
        String sellerNo = sellers.findById(l.getSellerId())
                .map(Seller::getSellerNo).orElse(null);
        return new ListingView(l.getListingNo(), l.getSellerId(), sellerNo, l.getProductCode(),
                l.getSessionCode(), l.getUnitPrice(), l.isPlatformGuaranteed(), l.getStatus().name());
    }

    private TicketUnitView toView(TicketUnit u) {
        String listingNo = listings.findById(u.getListingId())
                .map(Listing::getListingNo).orElse(null);
        return new TicketUnitView(u.getUnitNo(), listingNo, u.getStatus().name());
    }
}
