package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.audit.api.AuditFacade;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.VenueView;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentView;
import site.awn.payment.sandbox.SandboxPaymentGateway;
import site.awn.shared.alert.AlertCategory;
import site.awn.shared.alert.AlertRecorder;
import site.awn.shared.outbox.OutboxDispatchService;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 06：运营接口验收。
 * - 权限校验：X-Admin-Token 缺失/错误一律 401；
 * - 订单 / 库存 / 异常 / 审计视图使用真实数据；
 * - 库存调整要求原因并留审计；
 * - 三类告警（对账差异 / 支付事件投递失败 / 库存对账差异）出现在异常视图且可确认。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTests extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String TOKEN = "awn-admin-dev-token";

    @Autowired MockMvc mockMvc;
    @Autowired OrderFacade orderFacade;
    @Autowired InventoryFacade inventory;
    @Autowired EventFacade events;
    @Autowired PaymentFacade payments;
    @Autowired AuditFacade audit;
    @Autowired AlertRecorder alerts;
    @Autowired OutboxDispatchService dispatcher;
    @Autowired SandboxPaymentGateway sandbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired site.awn.support.CatalogProductFactory products;

    @Test
    void adminEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/anomalies").header(TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/inventory/adjustments").header(TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/anomalies").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void orderAndInventoryViewsReturnRealData() throws Exception {
        OrderView order = newOrder(2, 300);

        mockMvc.perform(get("/api/v1/admin/orders").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderNo == '" + order.orderNo() + "')]").exists());
        mockMvc.perform(get("/api/v1/admin/inventory").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.poolCode == '" + lastPoolCode + "')]").exists());
    }

    @Test
    void inventoryAdjustmentRequiresReasonWritesAuditAndUpdatesBalance() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix), "sess-" + suffix, 10));

        // 缺少操作原因 → 400
        mockMvc.perform(post("/api/v1/admin/inventory/adjustments").header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"poolCode\":\"" + poolCode + "\",\"delta\":5,\"actor\":\"cui\"}"))
                .andExpect(status().isBadRequest());

        // 合规调整 → 余额变化 + 审计留痕
        mockMvc.perform(post("/api/v1/admin/inventory/adjustments").header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"poolCode\":\"" + poolCode + "\",\"delta\":5,\"reason\":\"补货\",\"actor\":\"cui\"}"))
                .andExpect(status().isOk());
        BalanceView balance = inventory.balanceOf(poolCode);
        assertThat(balance.totalQuantity()).as("调整必须落到余额").isEqualTo(15);

        assertThat(audit.recent())
                .as("库存调整必须留审计")
                .anyMatch(entry -> "INVENTORY_ADJUSTED".equals(entry.action()) && poolCode.equals(entry.targetId()));

        mockMvc.perform(get("/api/v1/admin/audit-logs").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void eventPublishRequiresTokenOpensPublicVisibilityAndGuardsStateMachine() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-adm-" + suffix, "运营场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        EventView draft = events.createEvent(new CreateEventCommand(
                "e-adm-" + suffix, "运营发布演出", null, start, start.plusSeconds(7200), venue.id()));

        // 无令牌 → 401（AdminAuthFilter 覆盖 /api/v1/admin/**）
        mockMvc.perform(post("/api/v1/admin/events/" + draft.eventCode() + "/publish"))
                .andExpect(status().isUnauthorized());

        // 活动视图：全状态列表能看到草稿（发布流入口）；无令牌同样 401
        mockMvc.perform(get("/api/v1/admin/events"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/events").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventCode == '" + draft.eventCode() + "')].status")
                        .value(hasItem("DRAFT")));

        // 草稿发布 → 200 PUBLISHED，公开列表立即可见
        mockMvc.perform(post("/api/v1/admin/events/" + draft.eventCode() + "/publish")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(draft.eventCode()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventCode", hasItem(draft.eventCode())));

        // 重复发布 → 409 CONFLICT（状态机守卫）
        mockMvc.perform(post("/api/v1/admin/events/" + draft.eventCode() + "/publish")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // 未知活动 → 404 EVENT_NOT_FOUND
        mockMvc.perform(post("/api/v1/admin/events/e-missing-" + suffix + "/publish")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void reconciliationDifferenceRaisesAlertVisibleInAnomalies() throws Exception {
        OrderView order = newOrder(1, 500);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        // 渠道已成功、本地未跟上（漏回调）→ 对账差异
        sandbox.succeedCharge(payment.channelTxnNo());
        mockMvc.perform(get("/api/v1/admin/reconciliation").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());

        assertThat(alerts.openAlerts())
                .as("对账差异必须进入异常视图")
                .anyMatch(a -> a.getCategory() == AlertCategory.RECONCILIATION
                        && a.getReference().contains(payment.paymentNo()));
    }

    @Test
    void failedOutboxMessageRaisesPaymentAlertAndIsAcknowledgeable() throws Exception {
        // 投递必失败的支付事件（订单不存在），预置 attempts=4 → 一轮即转 FAILED
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String missingOrderNo = "no-such-" + suffix;
        Long outboxId = jdbc.queryForObject(
                "INSERT INTO outbox_messages (aggregate_type, aggregate_id, event_type, payload, status, attempts, next_attempt_at) "
                        + "VALUES ('ORDER', ?, 'PAYMENT_SUCCEEDED', ?::jsonb, 'PENDING', 4, now() - interval '1 second') "
                        + "RETURNING id",
                Long.class, missingOrderNo, "{\"orderNo\":\"" + missingOrderNo + "\"}");

        // 测试库积压历史到期消息，循环分发直到本消息转 FAILED
        for (int i = 0; i < 60; i++) {
            dispatcher.dispatchDue(Instant.now());
            String status = jdbc.queryForObject(
                    "SELECT status FROM outbox_messages WHERE id = ?", String.class, outboxId);
            if ("FAILED".equals(status)) {
                break;
            }
        }
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_messages WHERE id = ?", String.class, outboxId))
                .as("投递失败超上限必须转 FAILED").isEqualTo("FAILED");

        var alert = alerts.openAlerts().stream()
                .filter(a -> a.getCategory() == AlertCategory.PAYMENT
                        && String.valueOf(outboxId).equals(a.getReference()))
                .findFirst().orElseThrow(() -> new AssertionError("FAILED 事件必须触发支付异常告警"));

        // 确认后从异常视图消失
        mockMvc.perform(post("/api/v1/admin/anomalies/" + alert.getId() + "/acknowledge")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        assertThat(alerts.openAlerts())
                .as("确认后的告警不再出现在异常视图")
                .noneMatch(a -> a.getId().equals(alert.getId()));
    }

    @Test
    void inventoryReconciliationDifferenceRaisesAlert() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix), "sess-" + suffix, 10));

        String poolWhere = "WHERE inventory_pool_id = (SELECT id FROM inventory_pool WHERE pool_code = ?)";
        Integer original = jdbc.queryForObject(
                "SELECT reserved_quantity FROM inventory_balance " + poolWhere, Integer.class, poolCode);
        // 直接篡改余额计数器制造账实不符；测试库跨用例共享，断言完必须还原
        jdbc.update("UPDATE inventory_balance SET reserved_quantity = 2 " + poolWhere, poolCode);
        try {
            inventory.reconcile();

            assertThat(alerts.openAlerts())
                    .as("库存对账差异必须触发库存告警")
                    .anyMatch(a -> a.getCategory() == AlertCategory.INVENTORY
                            && a.getMessage().contains(poolCode));
        } finally {
            jdbc.update("UPDATE inventory_balance SET reserved_quantity = ? " + poolWhere, original, poolCode);
        }
    }

    // ------------------------------------------------------------------

    private String lastPoolCode;

    private OrderView newOrder(int quantity, long unitPrice) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String poolCode = "pool-" + suffix;
        lastPoolCode = poolCode;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix),
                "sess-" + suffix, quantity * 5));
        var line = new OrderLineCommand(poolCode, "prod-" + suffix, "运营接口测试演出",
                "sess-" + suffix, null, quantity, unitPrice, 0, 0);
        return orderFacade.createOrder(new CreateOrderCommand("adm-" + suffix, 42L, "CNY", List.of(line)));
    }
}
