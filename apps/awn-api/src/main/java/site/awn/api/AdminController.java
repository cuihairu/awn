package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.awn.audit.api.AuditEntryView;
import site.awn.audit.api.AuditFacade;
import site.awn.audit.api.RecordAuditCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderView;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentReconciliationReport;
import site.awn.shared.alert.AlertRecord;
import site.awn.shared.alert.AlertRecorder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营接口（契约 docs/api/contracts.md「运营接口」）。
 * 鉴权由 AdminAuthFilter 统一执行（X-Admin-Token）；库存调整要求原因并留审计。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final OrderFacade orders;
    private final InventoryFacade inventory;
    private final PaymentFacade payments;
    private final EventFacade events;
    private final AlertRecorder alerts;
    private final AuditFacade audit;

    public AdminController(OrderFacade orders, InventoryFacade inventory, PaymentFacade payments,
                           EventFacade events, AlertRecorder alerts, AuditFacade audit) {
        this.orders = orders;
        this.inventory = inventory;
        this.payments = payments;
        this.events = events;
        this.alerts = alerts;
        this.audit = audit;
    }

    /** 订单视图：最近订单（新→旧） */
    @GetMapping("/orders")
    public List<OrderView> recentOrders() {
        return orders.recentOrders();
    }

    /** 库存视图：全部池余额 */
    @GetMapping("/inventory")
    public List<site.awn.inventory.api.BalanceView> inventoryBalances() {
        return inventory.balances();
    }

    /** 库存调整（敏感操作：要求原因、写 ADJUST 流水、留审计） */
    @PostMapping("/inventory/adjustments")
    public Map<String, Object> adjustInventory(@RequestBody AdjustInventoryRequest request) {
        if (request.actor() == null || request.actor().isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        inventory.adjustPool(request.poolCode(), request.delta(), request.reason(), request.actor());
        audit.record(new RecordAuditCommand(request.actor(), "INVENTORY_ADJUSTED", "inventory_pool",
                request.poolCode(),
                "{\"delta\":" + request.delta() + ",\"reason\":\"" + request.reason().replace("\"", "'") + "\"}"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("poolCode", request.poolCode());
        result.put("delta", request.delta());
        return result;
    }

    /** 对账视图：支付/退款渠道对账报告（重复执行幂等） */
    @GetMapping("/reconciliation")
    public PaymentReconciliationReport reconciliation() {
        return payments.reconcile();
    }

    /** 异常视图：未确认告警（库存异常 / 支付异常 / 对账差异） */
    @GetMapping("/anomalies")
    public List<AlertView> openAlerts() {
        return alerts.openAlerts().stream().map(AdminController::toView).toList();
    }

    /** 审计视图：最近审计记录 */
    @GetMapping("/audit-logs")
    public List<AuditEntryView> auditLogs() {
        return audit.recent();
    }

    /** 活动发布（DRAFT → PUBLISHED）：仅草稿可发布，重复发布 409 CONFLICT；发布后公开读可见 */
    @PostMapping("/events/{eventCode}/publish")
    public EventView publishEvent(@PathVariable String eventCode) {
        return events.publishEvent(eventCode);
    }

    /** 告警确认 */
    @PostMapping("/anomalies/{id}/acknowledge")
    public Map<String, Object> acknowledgeAlert(@org.springframework.web.bind.annotation.PathVariable long id) {
        boolean ok = alerts.acknowledge(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acknowledged", ok);
        return result;
    }

    private static AlertView toView(AlertRecord alert) {
        return new AlertView(alert.getId(), alert.getCategory().name(), alert.getSeverity().name(),
                alert.getMessage(), alert.getReference(), alert.getCreatedAt());
    }

    public record AdjustInventoryRequest(String poolCode, int delta, String reason, String actor) {
    }

    public record AlertView(Long id, String category, String severity, String message,
                            String reference, java.time.Instant createdAt) {
    }
}
