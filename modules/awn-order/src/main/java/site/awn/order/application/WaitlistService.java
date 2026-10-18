package site.awn.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import site.awn.catalog.api.CatalogFacade;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.JoinWaitlistCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.api.WaitlistFacade;
import site.awn.order.api.WaitlistPromotionResult;
import site.awn.order.api.WaitlistView;
import site.awn.order.domain.OrderStatus;
import site.awn.order.domain.WaitlistConflictException;
import site.awn.order.domain.WaitlistEntry;
import site.awn.order.domain.WaitlistNotFoundException;
import site.awn.order.domain.WaitlistStatus;
import site.awn.order.infrastructure.WaitlistRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 排队 / 候补用例（契约「排队 / 候补」）。
 *
 * <p>排队只登记意愿：不占库存、不预生成订单。位次按同池 WAITING 条目的
 * （created_at, id）顺序派生计算，不落库排名字段——排队人数是派生值，
 * 落库会在并发递补与退出排队时失真。</p>
 *
 * <p>递补（{@link #promoteAvailable(Instant)}）分两步：已递补席位与订单状态对账，
 * 再按池 FIFO 为队首建单；建单幂等键取候补编号派生值，重跑返回同一订单，
 * 因此「预留成功但席位迁移前崩溃」重放不会重复建单。单条失败不影响其余条目。</p>
 */
@Service
public class WaitlistService implements WaitlistFacade {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlists;
    private final InventoryFacade inventory;
    private final OrderFacade orders;
    private final CatalogFacade catalog;
    private final TransactionTemplate transactions;

    WaitlistService(WaitlistRepository waitlists, InventoryFacade inventory,
                    OrderFacade orders, CatalogFacade catalog, TransactionTemplate transactions) {
        this.waitlists = waitlists;
        this.inventory = inventory;
        this.orders = orders;
        this.catalog = catalog;
        this.transactions = transactions;
    }

    @Override
    public WaitlistView join(JoinWaitlistCommand command) {
        if (command.quantity() <= 0) {
            throw new IllegalArgumentException("waitlist quantity must be positive");
        }
        if (command.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (command.currency() == null || command.currency().length() != 3) {
            throw new IllegalArgumentException("currency must be ISO-4217 alpha-3");
        }
        // 未知池先拒绝（PoolNotFoundException），再做同用户同池占用判重
        inventory.balanceOf(command.poolCode());
        // 审核整改 P0（luna-review 表一）：排队价即递补建单价，必须等于目录价——
        // 请求方自报价在入口拒绝；递补侧 createOrder 还有第二道核价兜底
        long catalogPrice = catalog.product(command.productId()).unitPrice();
        if (command.unitPrice() != catalogPrice) {
            throw new IllegalArgumentException("unit price mismatch for " + command.productId()
                    + ": server price " + catalogPrice + ", request " + command.unitPrice());
        }
        String lockKey = "wl-join-" + command.userId() + ":" + command.poolCode();
        WaitlistEntry saved = transactions.execute(status -> {
            waitlists.lockPoolJoin(lockKey);
            if (waitlists.existsByUserIdAndPoolCodeAndStatusIn(command.userId(), command.poolCode(),
                    List.of(WaitlistStatus.WAITING, WaitlistStatus.PROMOTED))) {
                throw new WaitlistConflictException(
                        "user already occupies a waitlist seat in pool: " + command.poolCode());
            }
            WaitlistEntry entry = new WaitlistEntry("WL-" + UUID.randomUUID(), command.userId(),
                    command.sessionId(), command.poolCode(), command.productId(),
                    command.productName(), command.quantity(), command.unitPrice(), command.currency());
            return waitlists.saveAndFlush(entry);
        });
        // 事务外重读：created_at 由数据库 DEFAULT 维护，实体只读，重查才拿到真实值
        return waitlist(saved.getWaitlistNo());
    }

    @Override
    @Transactional(readOnly = true)
    public WaitlistView waitlist(String waitlistNo) {
        WaitlistEntry entry = waitlists.findByWaitlistNo(waitlistNo)
                .orElseThrow(() -> new WaitlistNotFoundException(waitlistNo));
        return toView(entry);
    }

    @Override
    public boolean cancel(String waitlistNo) {
        // 守卫迁移与幂等判定必须同事务：迁移是 flush 的原生更新，不能在事务外执行
        return transactions.execute(status -> {
            WaitlistEntry entry = waitlists.findByWaitlistNo(waitlistNo)
                    .orElseThrow(() -> new WaitlistNotFoundException(waitlistNo));
            if (waitlists.transition(waitlistNo, WaitlistStatus.WAITING, WaitlistStatus.CANCELLED) == 1) {
                return true;
            }
            // 守卫更新输家：重读实际状态。CANCELLED 为幂等重放；其余状态为非法操作
            WaitlistStatus current = waitlists.findByWaitlistNo(waitlistNo)
                    .map(WaitlistEntry::getStatus)
                    .orElseThrow(() -> new WaitlistNotFoundException(waitlistNo));
            if (current == WaitlistStatus.CANCELLED) {
                return false;
            }
            throw new WaitlistConflictException("waitlist no longer cancellable: " + current);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistView> waitlistOf(long userId) {
        return waitlists.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public WaitlistPromotionResult promoteAvailable(Instant now) {
        int promoted = 0;
        int claimed = 0;
        int expired = 0;
        List<String> failed = new ArrayList<>();
        // 1) 已递补席位与订单状态对账：库存已释放的席位过期回池，已支付的成单
        for (WaitlistEntry entry : waitlists.findByStatusOrderByCreatedAtAscIdAsc(WaitlistStatus.PROMOTED)) {
            try {
                if (entry.getOrderNo() == null) {
                    // 不变量破坏：已递补席位必须带订单号（抢占与绑单同事务）
                    throw new IllegalStateException(
                            "waitlist seat promoted without order: " + entry.getWaitlistNo());
                }
                WaitlistStatus target = seatTargetFor(orders.orderOf(entry.getOrderNo()).status());
                if (target == null) {
                    // 待支付 / 履约中：席位继续等订单结论
                    continue;
                }
                Boolean moved = transactions.execute(tx ->
                        waitlists.transition(entry.getWaitlistNo(), WaitlistStatus.PROMOTED, target) == 1);
                if (Boolean.TRUE.equals(moved)) {
                    if (target == WaitlistStatus.CLAIMED) {
                        claimed++;
                    } else {
                        expired++;
                    }
                }
            } catch (RuntimeException e) {
                // 异常路径显式上报，不允许静默吞掉（巡检红线：不许只报在做）
                log.error("waitlist seat reconciliation failed: {}", entry.getWaitlistNo(), e);
                failed.add(entry.getWaitlistNo());
            }
        }
        // 2) 按池 FIFO 递补：队首抢占席位并在同事务内建单，库存仍不足整池保持排队
        for (String poolCode : waitlists.findPoolCodesByStatus(WaitlistStatus.WAITING)) {
            for (WaitlistEntry head : waitlists.findByPoolCodeAndStatusOrderByCreatedAtAscIdAsc(
                    poolCode, WaitlistStatus.WAITING)) {
                try {
                    if (promoteOne(head)) {
                        promoted++;
                    }
                } catch (InsufficientInventoryException e) {
                    // 库存仍不足：队首不让位，整池保持排队等下一轮
                    break;
                } catch (RuntimeException e) {
                    log.error("waitlist promotion failed: {}", head.getWaitlistNo(), e);
                    failed.add(head.getWaitlistNo());
                    break;
                }
            }
        }
        return new WaitlistPromotionResult(promoted, claimed, expired, failed);
    }

    /** 订单已支付 → 席位成单；订单已超时或取消 → 席位过期回池；其余状态继续等结论 */
    private WaitlistStatus seatTargetFor(OrderStatus orderStatus) {
        return switch (orderStatus) {
            case PAID, FULFILLING, FULFILLED, REFUND_PENDING, REFUNDED -> WaitlistStatus.CLAIMED;
            case EXPIRED, CANCELLED -> WaitlistStatus.EXPIRED;
            default -> null;
        };
    }

    /**
     * 单条递补，运行在独立事务内：守卫抢占席位 → 建单 → 绑单三步同事务提交。
     * 库存预留失败（InsufficientInventoryException）整事务回滚，席位回到 WAITING
     * 等下一轮；因此不存在「已建单未绑单」的半提交状态。
     * 建单幂等键取候补编号派生值，重跑返回同一订单，不会重复预留库存。
     */
    private boolean promoteOne(WaitlistEntry head) {
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            if (waitlists.transition(head.getWaitlistNo(), WaitlistStatus.WAITING,
                    WaitlistStatus.PROMOTED) == 0) {
                // 并发抢输：其他调度方已占用该席位
                return false;
            }
            // Queue price was checked against the catalog at join time and is an immutable user quote.
            OrderView order = orders.createWaitlistPromotionOrder(new CreateOrderCommand(
                    "wl-promo-" + head.getWaitlistNo(), head.getUserId(), head.getCurrency(),
                    List.of(new OrderLineCommand(head.getPoolCode(), head.getProductId(),
                            head.getProductName(), head.getSessionId(), null,
                            head.getQuantity(), head.getUnitPrice(), 0, 0))));
            if (waitlists.bindOrder(head.getWaitlistNo(), WaitlistStatus.PROMOTED, order.orderNo()) == 0) {
                // 抢占后席位被并发迁移（对账任务先跑完）：整事务回滚，避免孤立订单
                throw new WaitlistConflictException(
                        "waitlist seat moved during promotion: " + head.getWaitlistNo());
            }
            return true;
        }));
    }

    private WaitlistView toView(WaitlistEntry entry) {
        Long payExpiresInSeconds = null;
        if (entry.getOrderNo() != null) {
            payExpiresInSeconds = orders.orderOf(entry.getOrderNo()).payExpiresInSeconds();
        }
        return new WaitlistView(entry.getWaitlistNo(), entry.getStatus().name(), entry.getUserId(),
                entry.getSessionId(), entry.getPoolCode(), entry.getProductId(), entry.getProductName(),
                entry.getQuantity(), entry.getUnitPrice(), entry.getCurrency(),
                positionOf(entry), entry.getOrderNo(), payExpiresInSeconds, entry.getCreatedAt());
    }

    /** 同池 WAITING 队列位次（从 1 起），非排队状态为 null */
    private Integer positionOf(WaitlistEntry entry) {
        if (entry.getStatus() != WaitlistStatus.WAITING) {
            return null;
        }
        List<WaitlistEntry> queue = waitlists.findByPoolCodeAndStatusOrderByCreatedAtAscIdAsc(
                entry.getPoolCode(), WaitlistStatus.WAITING);
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getWaitlistNo().equals(entry.getWaitlistNo())) {
                return i + 1;
            }
        }
        return null;
    }
}
