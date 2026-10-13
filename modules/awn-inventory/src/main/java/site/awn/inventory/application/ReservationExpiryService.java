package site.awn.inventory.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import site.awn.inventory.api.ReservationExpiryResult;
import site.awn.inventory.domain.ReservationStatus;
import site.awn.inventory.infrastructure.InventoryReservationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 预留到期释放（Worker 调用）。
 *
 * <p>带 TTL 的预留过期后，由本服务扫描并逐条释放，库存回补。
 * 幂等由 {@link ReservationLifecycleService#release} 的状态机守卫保证：
 * 重复释放返回 false，不重复回补。单条异常只进 failed 清单，不阻塞整批。</p>
 */
@Service
public class ReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryService.class);

    /** 单轮批量上限：防止一次性锁住过多预留 */
    private static final int BATCH_LIMIT = 200;

    private final InventoryReservationRepository reservations;
    private final ReservationLifecycleService lifecycleService;

    ReservationExpiryService(InventoryReservationRepository reservations,
                             ReservationLifecycleService lifecycleService) {
        this.reservations = reservations;
        this.lifecycleService = lifecycleService;
    }

    public ReservationExpiryResult releaseExpiredReservations(Instant now) {
        List<String> expired = reservations.findExpiredActive(
                ReservationStatus.ACTIVE, now, Pageable.ofSize(BATCH_LIMIT));
        int released = 0;
        List<String> failed = new ArrayList<>();
        for (String reservationNo : expired) {
            try {
                if (lifecycleService.release(reservationNo)) {
                    released++;
                }
            } catch (RuntimeException e) {
                // 异常路径显式上报，不允许静默吞掉（巡检红线：不许只报在做）
                log.error("reservation expiry release failed: {}", reservationNo, e);
                failed.add(reservationNo);
            }
        }
        return new ReservationExpiryResult(released, failed);
    }
}
