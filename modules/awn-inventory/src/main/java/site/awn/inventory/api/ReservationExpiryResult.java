package site.awn.inventory.api;

import java.util.List;

/**
 * 预留到期扫描批处理结果。
 *
 * @param released 本轮成功释放的预留数（ACTIVE → RELEASED 且库存回补）
 * @param failed   释放过程抛异常的预留号（进入告警，绝不静默吞掉）
 */
public record ReservationExpiryResult(int released, List<String> failed) {

    public ReservationExpiryResult {
        failed = failed == null ? List.of() : List.copyOf(failed);
    }
}
