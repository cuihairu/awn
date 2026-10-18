package site.awn.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.marketplace.api.CreateListingCommand;
import site.awn.marketplace.api.CreateSellerCommand;
import site.awn.marketplace.api.ListingView;
import site.awn.marketplace.api.MarketplaceFacade;
import site.awn.marketplace.api.SubmitSupplyReviewCommand;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderStatus;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;
import site.awn.api.auth.ApiTokenService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P05 争议处理（docs/architecture/dispute-handling.md）：
 * 买家按订单开争议（订单本人 / PAID-FULFILLING-FULFILLED / 同订单无在途 / 金额≤应付 /
 * 凭证 URL 白名单），卖家归属在开单时从凭证行快照；买家可撤回未受理争议；
 * 受理与裁决仅平台管理员（X-Admin-Token），裁决三种结论并留痕，重复裁决拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisputeModuleTests extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String TOKEN = "awn-admin-dev-token";

    @Autowired OrderFacade orderFacade;
    @Autowired MarketplaceFacade marketplace;
    @Autowired InventoryFacade inventory;
    @Autowired CatalogProductFactory products;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApiTokenService apiTokens;

    // ------------------------------------------------------------------
    // 买家面：开单
    // ------------------------------------------------------------------

    @Test
    void buyerOpensDisputeOnPaidVoucherOrderAndSellerIsSnapshotted() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);

        String body = """
                {"userId":42,"reason":"NOT_DELIVERED","description":"付款三天未交付",
                 "requestedResolution":"REFUND","requestedAmount":6800}
                """;
        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedBy").value(42))
                .andExpect(jsonPath("$.listingNo").value(listing.listingNo()))
                .andExpect(jsonPath("$.unitNo").isNotEmpty())
                .andExpect(jsonPath("$.sellerId").value(listing.sellerId().intValue()))
                .andExpect(jsonPath("$.resolution").doesNotExist())
                .andExpect(jsonPath("$.decidedBy").doesNotExist());

        // 争议是旁路记录：不影响订单状态机
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void onlyTheOrderBuyerCanOpenADispute() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);

        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .header("Authorization", bearer(999L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"reason\":\"OTHER\",\"description\":\"x\","
                                + "\"requestedResolution\":\"REFUND\",\"requestedAmount\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));
    }

    @Test
    void orderMustBeDisputableToOpenADispute() throws Exception {
        // 待支付订单无争议基础
        ListingView listing = listedListing();
        OrderView pending = orderFacade.createOrder(
                command("dsp-pend-" + uid(), voucherLine(listing, "演出 DP")));
        mockMvc.perform(post("/api/v1/orders/" + pending.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"OTHER\",\"description\":\"x\","
                                + "\"requestedResolution\":\"REFUND\",\"requestedAmount\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));

        // 已退款订单走退款流，不可再开争议
        ListingView listing2 = listedListing();
        OrderView paid = paidVoucherOrder(listing2);
        jdbc.update("UPDATE orders SET status = 'REFUNDED' WHERE order_no = ?", paid.orderNo());
        mockMvc.perform(post("/api/v1/orders/" + paid.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"OTHER\",\"description\":\"x\","
                                + "\"requestedResolution\":\"REFUND\",\"requestedAmount\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));
    }

    @Test
    void oneOpenDisputePerOrderUntilWithdrawn() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);
        String body = """
                {"userId":42,"reason":"NOT_DELIVERED","description":"未交付",
                 "requestedResolution":"REFUND","requestedAmount":6800}
                """;
        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // 在途期间重复开单拒绝
        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));

        // 撤回后可以再开：终态不占位
        String disputeNo = openDisputeNo(order.orderNo());
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void refundAmountMustBeWithinPayAmountAndRedeliverMustNotCarryAmount() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);
        String base = "/api/v1/orders/" + order.orderNo() + "/disputes";

        // 超应付拒绝
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"NOT_DELIVERED\",\"description\":\"x\","
                                + "\"requestedResolution\":\"REFUND\",\"requestedAmount\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 零金额拒绝
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"NOT_DELIVERED\",\"description\":\"x\","
                                + "\"requestedResolution\":\"REFUND\",\"requestedAmount\":0}"))
                .andExpect(status().isBadRequest());
        // REFUND 缺金额拒绝
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"NOT_DELIVERED\",\"description\":\"x\","
                                + "\"requestedResolution\":\"REFUND\"}"))
                .andExpect(status().isBadRequest());
        // REDELIVER 带金额拒绝
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"TICKET_INVALID\",\"description\":\"x\","
                                + "\"requestedResolution\":\"REDELIVER\",\"requestedAmount\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evidenceUrlMustUseWhitelistedStorageDomain() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);

        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"OTHER\",\"description\":\"x\","
                                + "\"evidenceUrl\":\"https://evil.example.com/fake.png\","
                                + "\"requestedResolution\":\"REFUND\",\"requestedAmount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void buyerListsDisputesOfOwnOrderOnly() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);
        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"DESCRIPTION_MISMATCH\",\"description\":\"座次不符\","
                                + "\"requestedResolution\":\"REDELIVER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders/" + order.orderNo() + "/disputes").param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("DESCRIPTION_MISMATCH"))
                .andExpect(jsonPath("$[0].requestedAmount").doesNotExist());
        // 查询参数不能冒充身份；服务端只使用 token subject
        mockMvc.perform(get("/api/v1/orders/" + order.orderNo() + "/disputes").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ------------------------------------------------------------------
    // 买家面：撤回
    // ------------------------------------------------------------------

    @Test
    void onlyTheOpenerCanWithdrawAndOnlyBeforeAcceptance() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);
        String disputeNo = openDispute(order.orderNo(), "REFUND", 6800L);

        // 非开单人撤回拒绝
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/withdraw")
                        .header("Authorization", bearer(999L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));

        // 受理后买家不能单方面撤回（只能由平台裁决关闭）
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/accept")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));
    }

    // ------------------------------------------------------------------
    // 管理面：受理与裁决
    // ------------------------------------------------------------------

    @Test
    void adminAcceptsThenAdjudicatesWithTrace() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);
        String disputeNo = openDispute(order.orderNo(), "REFUND", 6800L);

        // 受理必须带管理员令牌：买家与卖家没有任何受理或裁决入口
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/accept"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/adjudicate")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"ops-a\",\"resolution\":\"REJECTED\",\"note\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));

        // 受理：OPEN → UNDER_REVIEW
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/accept")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // 裁决三种结论，结论与留痕一次落定
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/adjudicate")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"ops-a\",\"resolution\":\"REFUND_APPROVED\","
                                + "\"note\":\"核实未交付，按既有退款流执行\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("REFUND_APPROVED"))
                .andExpect(jsonPath("$.resolutionNote").value("核实未交付，按既有退款流执行"))
                .andExpect(jsonPath("$.decidedBy").value("ops-a"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // 终态拒绝一切迁移：重复裁决、再受理、买家撤回全部拒绝
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/adjudicate")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"ops-a\",\"resolution\":\"REJECTED\",\"note\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/accept")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42}"))
                .andExpect(status().isConflict());

        // 运营后台争议列表可见
        mockMvc.perform(get("/api/v1/admin/disputes").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.disputeNo == '" + disputeNo + "')]").isNotEmpty());
    }

    @Test
    void adjudicateRequiresResolutionAndNote() throws Exception {
        ListingView listing = listedListing();
        OrderView order = paidVoucherOrder(listing);
        String disputeNo = openDispute(order.orderNo(), "REDELIVER", null);
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/accept")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());

        // note 必填
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/adjudicate")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"ops-a\",\"resolution\":\"REDELIVER_APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 未知裁决结论拒绝
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/adjudicate")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"ops-a\",\"resolution\":\"MAYBE\",\"note\":\"x\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/adjudicate")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"ops-a\",\"resolution\":\"REDELIVER_APPROVED\","
                                + "\"note\":\"协调卖家重新出票\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("REDELIVER_APPROVED"));
    }

    // ------------------------------------------------------------------
    // 归属快照与边界
    // ------------------------------------------------------------------

    @Test
    void platformPoolOrderDisputeHasNoSellerSnapshot() throws Exception {
        PoolRef ref = pool("dsp-pool", 3);
        OrderView order = orderFacade.createOrder(command("dsp-pool-" + uid(),
                new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 POOL", "sess-pool", null, 1, 10000, 0, 0)));
        orderFacade.markPaid(order.orderNo());

        mockMvc.perform(post("/api/v1/orders/" + order.orderNo() + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"OTHER\",\"description\":\"平台票务问题\","
                                + "\"requestedResolution\":\"REFUND\",\"requestedAmount\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").doesNotExist())
                .andExpect(jsonPath("$.listingNo").doesNotExist())
                .andExpect(jsonPath("$.unitNo").doesNotExist());
    }

    @Test
    void unknownDisputeIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/disputes/DSP-none/withdraw")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/admin/disputes/DSP-none/accept")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // 卖家面：查归属争议与申辩
    // ------------------------------------------------------------------

    @Test
    void sellerListsDisputesOfOwnListingsOnly() throws Exception {
        Listed listed = listedWithSeller();
        OrderView order = paidVoucherOrder(listed.listing());
        String disputeNo = openDispute(order.orderNo(), "REFUND", 6800L);

        // 卖家按开单时快照的挂牌卖家归属可见
        mockMvc.perform(get("/api/v1/sellers/" + listed.sellerNo() + "/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disputeNo").value(disputeNo))
                .andExpect(jsonPath("$[0].sellerId").value(listed.listing().sellerId().intValue()));

        // 另一卖家看不到
        String other = marketplace.registerSeller(
                new CreateSellerCommand(999L, "卖家-其他", null)).sellerNo();
        mockMvc.perform(get("/api/v1/sellers/" + other + "/disputes")
                        .header("Authorization", bearer(999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // 未知卖家 404
        mockMvc.perform(get("/api/v1/sellers/SEL-none/disputes"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sellerSubmitsResponseOnlyForOwnInFlightDispute() throws Exception {
        Listed listed = listedWithSeller();
        OrderView order = paidVoucherOrder(listed.listing());
        String disputeNo = openDispute(order.orderNo(), "REFUND", 6800L);

        // 申辩成功：追加留痕
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/seller-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerNo\":\"" + listed.sellerNo()
                                + "\",\"content\":\"已联系买家补票\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerNo").value(listed.sellerNo()))
                .andExpect(jsonPath("$.content").value("已联系买家补票"));

        // 管理面可读（裁决依据）
        mockMvc.perform(get("/api/v1/admin/disputes/" + disputeNo + "/seller-responses")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("已联系买家补票"));

        // 非快照卖家申辩拒绝
        String other = marketplace.registerSeller(
                new CreateSellerCommand(999L, "卖家-其他", null)).sellerNo();
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/seller-response")
                        .header("Authorization", bearer(999L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerNo\":\"" + other + "\",\"content\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));

        // 未知卖家 404
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/seller-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerNo\":\"SEL-none\",\"content\":\"x\"}"))
                .andExpect(status().isNotFound());

        // 空内容 400
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/seller-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerNo\":\"" + listed.sellerNo() + "\",\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 未知争议 404
        mockMvc.perform(post("/api/v1/disputes/DSP-none/seller-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerNo\":\"" + listed.sellerNo() + "\",\"content\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISPUTE_NOT_FOUND"));
    }

    @Test
    void platformPoolOrderDisputeAcceptsNoSellerResponse() throws Exception {
        // 纯平台库存订单无卖家快照：无卖家可申辩
        PoolRef ref = pool("dsp-pool2", 3);
        OrderView order = orderFacade.createOrder(command("dsp-pool2-" + uid(),
                new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 POOL", "sess-pool", null, 1, 10000, 0, 0)));
        orderFacade.markPaid(order.orderNo());
        String disputeNo = openDispute(order.orderNo(), "REFUND", 5000L);

        Listed listed = listedWithSeller();
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/seller-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerNo\":\"" + listed.sellerNo() + "\",\"content\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));
    }

    @Test
    void sellerCannotRespondAfterResolution() throws Exception {
        Listed listed = listedWithSeller();
        OrderView order = paidVoucherOrder(listed.listing());
        String disputeNo = openDispute(order.orderNo(), "REFUND", 6800L);
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/accept")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeNo + "/adjudicate")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"ops-a\",\"resolution\":\"REJECTED\",\"note\":\"x\"}"))
                .andExpect(status().isOk());

        // 终态争议不接受申辩
        mockMvc.perform(post("/api/v1/disputes/" + disputeNo + "/seller-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerNo\":\"" + listed.sellerNo() + "\",\"content\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CONFLICT"));
    }

    // ------------------------------------------------------------------
    // 帮助方法
    // ------------------------------------------------------------------

    /** 已支付凭证订单：卖家挂牌 → 下单 → 支付确认 */
    private OrderView paidVoucherOrder(ListingView listing) {
        OrderView order = orderFacade.createOrder(
                command("dsp-" + uid(), voucherLine(listing, "演出 D")));
        orderFacade.markPaid(order.orderNo());
        return orderFacade.orderOf(order.orderNo());
    }

    /** 开争议并返回争议单号（该订单此时恰有一张 OPEN 争议） */
    private String openDispute(String orderNo, String resolution, Long amount) throws Exception {
        String amountPart = amount == null ? "" : ",\"requestedAmount\":" + amount;
        mockMvc.perform(post("/api/v1/orders/" + orderNo + "/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"reason\":\"NOT_DELIVERED\",\"description\":\"未交付\","
                                + "\"requestedResolution\":\"" + resolution + "\"" + amountPart + "}"))
                .andExpect(status().isCreated());
        return openDisputeNo(orderNo);
    }

    private String openDisputeNo(String orderNo) {
        return jdbc.queryForObject(
                "SELECT dispute_no FROM order_disputes WHERE order_no = ? AND status = 'OPEN'",
                String.class, orderNo);
    }

    /** 凭证型行：商品编码与单价取自挂牌（卖家定价为权威价，审核整改 P0） */
    private OrderLineCommand voucherLine(ListingView listing, String name) {
        return new OrderLineCommand(null, listing.listingNo(), listing.productCode(),
                name, null, null, 1, listing.unitPrice(), 0, 0);
    }

    /** 卖家 + 供货审核 + 挂牌（卖家自担） */
    private ListingView listedListing() {
        return listedWithSeller().listing();
    }

    /** 挂牌连同归属卖家号（卖家面测试用） */
    private Listed listedWithSeller() {
        String sellerNo = marketplace.registerSeller(new CreateSellerCommand(
                42L,
                "卖家-" + UUID.randomUUID(), null)).sellerNo();
        var review = marketplace.submitSupplyReview(new SubmitSupplyReviewCommand(
                sellerNo, "prod-" + UUID.randomUUID().toString().substring(0, 8), null,
                2, 6800, null));
        ListingView listing = marketplace.createListing(new CreateListingCommand(
                sellerNo, review.reviewNo(), null, 6800, false));
        return new Listed(listing, sellerNo);
    }

    private record Listed(ListingView listing, String sellerNo) {
    }

    private CreateOrderCommand command(String key, OrderLineCommand... lines) {
        return new CreateOrderCommand(key, 42L, "CNY", List.of(lines));
    }

    /** 池 + 其真实商品（核价契约后行 productId 必须用池的商品编码） */
    private record PoolRef(String poolCode, String productCode) {
    }

    private PoolRef pool(String productPrefix, int total) {
        String productCode = products.productCode(productPrefix);
        String poolCode = "pool-" + UUID.randomUUID().toString().substring(0, 12);
        inventory.createPool(new CreatePoolCommand(poolCode, productCode,
                "sess-" + UUID.randomUUID().toString().substring(0, 6), total));
        return new PoolRef(poolCode, productCode);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    private String bearer(long userId) {
        return "Bearer " + apiTokens.issue(userId);
    }
}
