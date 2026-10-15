package site.awn.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.marketplace.api.ListingView;
import site.awn.marketplace.api.MarketplaceFacade;
import site.awn.order.api.AdjudicateDisputeCommand;
import site.awn.order.api.DisputeFacade;
import site.awn.order.api.DisputeSellerResponseView;
import site.awn.order.api.DisputeView;
import site.awn.order.api.OpenDisputeCommand;
import site.awn.order.api.SubmitSellerResponseCommand;
import site.awn.order.domain.DisputeResolution;
import site.awn.order.domain.DisputeRequestType;
import site.awn.order.domain.DisputeSellerResponse;
import site.awn.order.domain.DisputeStatus;
import site.awn.order.domain.Order;
import site.awn.order.domain.OrderDispute;
import site.awn.order.domain.OrderDisputeConflictException;
import site.awn.order.domain.OrderItem;
import site.awn.order.domain.OrderNotFoundException;
import site.awn.order.domain.OrderStatus;
import site.awn.order.domain.DisputeNotFoundException;
import site.awn.order.infrastructure.OrderDisputeRepository;
import site.awn.order.infrastructure.OrderDisputeSellerResponseRepository;
import site.awn.order.infrastructure.OrderItemRepository;
import site.awn.order.infrastructure.OrderRepository;
import site.awn.shared.url.StorageUrlValidator;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 争议受理与裁决（docs/architecture/dispute-handling.md）。
 * 争议是订单旁路记录：不阻塞订单状态机，裁决不动钱。
 * 买家面守卫（订单归属 / 可争议状态 / 在途判重 / 金额上限）在本层裁决。
 */
@Service
public class DisputeService implements DisputeFacade {

    /** 只有已付款且进入履约/交付的订单才有争议基础；退款中/已退款走退款流 */
    private static final java.util.Set<OrderStatus> DISPUTABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.FULFILLING, OrderStatus.FULFILLED);

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderDisputeRepository disputes;
    private final OrderDisputeSellerResponseRepository sellerResponses;
    private final MarketplaceFacade marketplace;

    public DisputeService(OrderRepository orders, OrderItemRepository orderItems,
                          OrderDisputeRepository disputes,
                          OrderDisputeSellerResponseRepository sellerResponses,
                          MarketplaceFacade marketplace) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.disputes = disputes;
        this.sellerResponses = sellerResponses;
        this.marketplace = marketplace;
    }

    @Override
    @Transactional
    public DisputeView openDispute(OpenDisputeCommand command) {
        Order order = orders.findByOrderNo(command.orderNo())
                .orElseThrow(() -> new OrderNotFoundException(command.orderNo()));
        if (!order.getUserId().equals(command.userId())) {
            throw new OrderDisputeConflictException(
                    "only the order buyer can open a dispute: " + command.orderNo());
        }
        if (!DISPUTABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new OrderDisputeConflictException(
                    "order is not disputable in status " + order.getStatus() + ": " + command.orderNo());
        }
        if (command.description() == null || command.description().isBlank()) {
            throw new IllegalArgumentException("dispute description is required");
        }
        DisputeRequestType requestType = command.requestedResolution();
        if (requestType == DisputeRequestType.REFUND) {
            Long amount = command.requestedAmount();
            if (amount == null || amount <= 0 || amount > order.getPayAmount()) {
                throw new IllegalArgumentException(
                        "refund dispute requires amount in (0, " + order.getPayAmount() + "]: " + amount);
            }
        } else if (command.requestedAmount() != null) {
            throw new IllegalArgumentException("redeliver dispute must not request an amount");
        }
        if (command.evidenceUrl() != null && !command.evidenceUrl().isBlank()) {
            StorageUrlValidator.validate(command.evidenceUrl());
        }
        if (disputes.existsByOrderNoAndStatusIn(command.orderNo(),
                EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))) {
            throw new OrderDisputeConflictException(
                    "an open dispute already exists for order: " + command.orderNo());
        }
        // 卖家归属快照：取该订单第一条凭证型行对应的挂牌卖家；纯平台库存行订单为 null
        OrderItem voucherLine = orderItems.findByOrderId(order.getId()).stream()
                .filter(item -> item.getListingNo() != null)
                .findFirst()
                .orElse(null);
        Long sellerId = null;
        if (voucherLine != null && voucherLine.getListingNo() != null) {
            // 挂牌只有下架没有删除，凭证行引用必然可解析；解析失败则如实失败（不留空归属蒙混）
            sellerId = marketplace.listingOf(voucherLine.getListingNo()).sellerId();
        }
        OrderDispute dispute = OrderDispute.open(
                "DSP-" + UUID.randomUUID(),
                order.getOrderNo(),
                order.getUserId(),
                sellerId,
                voucherLine == null ? null : voucherLine.getListingNo(),
                voucherLine == null ? null : voucherLine.getUnitNo(),
                command.reason(),
                command.description(),
                command.evidenceUrl(),
                requestType,
                command.requestedAmount());
        return toView(disputes.save(dispute));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeView> disputesOfOrder(String orderNo, Long userId) {
        Order order = orders.findByOrderNo(orderNo)
                .orElseThrow(() -> new OrderNotFoundException(orderNo));
        if (!order.getUserId().equals(userId)) {
            throw new OrderDisputeConflictException(
                    "only the order buyer can view disputes of: " + orderNo);
        }
        return disputes.findByOrderNoOrderByCreatedAtDesc(orderNo).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeView> recentDisputes() {
        return disputes.findTop50ByOrderByIdDesc().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public DisputeView withdraw(String disputeNo, Long userId) {
        OrderDispute dispute = disputeOf(disputeNo);
        if (!dispute.getOpenedBy().equals(userId)) {
            throw new OrderDisputeConflictException(
                    "only the opener can withdraw dispute: " + disputeNo);
        }
        dispute.withdraw();
        return toView(dispute);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeView> disputesOfSeller(String sellerNo) {
        Long sellerId = marketplace.sellerIdOf(sellerNo);
        return disputes.findTop50BySellerIdOrderByIdDesc(sellerId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public DisputeSellerResponseView submitSellerResponse(SubmitSellerResponseCommand command) {
        OrderDispute dispute = disputeOf(command.disputeNo());
        if (command.sellerNo() == null || command.sellerNo().isBlank()) {
            throw new IllegalArgumentException("sellerNo is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("seller response content is required");
        }
        Long sellerId = marketplace.sellerIdOf(command.sellerNo());
        if (dispute.getSellerId() == null || !dispute.getSellerId().equals(sellerId)) {
            throw new OrderDisputeConflictException(
                    "seller does not match dispute snapshot: " + command.sellerNo());
        }
        if (dispute.getStatus() != DisputeStatus.OPEN
                && dispute.getStatus() != DisputeStatus.UNDER_REVIEW) {
            throw new OrderDisputeConflictException(
                    "dispute is not open for seller responses: " + dispute.getStatus());
        }
        DisputeSellerResponse response = DisputeSellerResponse.submit(
                dispute.getDisputeNo(), command.sellerNo(), command.content());
        return toResponseView(sellerResponses.save(response));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeSellerResponseView> sellerResponsesOf(String disputeNo) {
        disputeOf(disputeNo);
        return sellerResponses.findByDisputeNoOrderByCreatedAtAsc(disputeNo).stream()
                .map(this::toResponseView)
                .toList();
    }

    @Override
    @Transactional
    public DisputeView accept(String disputeNo) {
        OrderDispute dispute = disputeOf(disputeNo);
        dispute.accept();
        return toView(dispute);
    }

    @Override
    @Transactional
    public DisputeView adjudicate(AdjudicateDisputeCommand command) {
        OrderDispute dispute = disputeOf(command.disputeNo());
        dispute.adjudicate(command.resolution(), command.note(), command.actor(), Instant.now());
        return toView(dispute);
    }

    private OrderDispute disputeOf(String disputeNo) {
        return disputes.findByDisputeNo(disputeNo)
                .orElseThrow(() -> new DisputeNotFoundException(disputeNo));
    }

    private DisputeView toView(OrderDispute d) {
        return new DisputeView(
                d.getDisputeNo(),
                d.getOrderNo(),
                d.getOpenedBy(),
                d.getSellerId(),
                d.getListingNo(),
                d.getUnitNo(),
                d.getReason(),
                d.getDescription(),
                d.getEvidenceUrl(),
                d.getRequestedResolution(),
                d.getRequestedAmount(),
                d.getStatus(),
                d.getResolution(),
                d.getResolutionNote(),
                d.getDecidedBy(),
                d.getDecidedAt(),
                d.getCreatedAt());
    }

    private DisputeSellerResponseView toResponseView(DisputeSellerResponse r) {
        return new DisputeSellerResponseView(r.getDisputeNo(), r.getSellerNo(), r.getContent(),
                r.getCreatedAt());
    }
}
