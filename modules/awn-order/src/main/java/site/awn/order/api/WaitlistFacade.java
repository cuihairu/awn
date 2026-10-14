package site.awn.order.api;

import java.time.Instant;
import java.util.List;

/**
 * 排队 / 候补对外接口（契约「排队 / 候补」）。
 *
 * <p>候补只登记排队意愿：条目不占库存、不预生成订单。库存释放后由
 * {@link #promoteAvailable(Instant)} 按池 FIFO 为队首原子预留并生成待支付订单
 * （与普通下单同源、同一金额与倒计时口径）；未在支付截止期内支付则席位过期，
 * 库存顺延给下一位。其他模块只允许通过本接口使用候补能力。</p>
 */
public interface WaitlistFacade {

    /**
     * 加入候补：登记排队意愿，返回带位次的候补条目。
     *
     * @throws site.awn.inventory.domain.PoolNotFoundException 池不存在
     * @throws site.awn.order.domain.WaitlistConflictException 同用户同池已占用席位（排队中或已递补）
     */
    WaitlistView join(JoinWaitlistCommand command);

    WaitlistView waitlist(String waitlistNo);

    /**
     * 退出排队。仅排队中（WAITING）可退出：重复取消幂等返回 false；
     * 已递补或已终态抛冲突（已递补的席位随订单走，走订单取消）。
     */
    boolean cancel(String waitlistNo);

    /** 某用户的候补列表（加入时间倒序） */
    List<WaitlistView> waitlistOf(long userId);

    /**
     * 递补扫描（Worker 调用）：先把已递补席位与订单状态对账
     * （订单已支付 → CLAIMED，订单已超时/取消 → EXPIRED），再按池 FIFO 递补。
     * 单条失败进 failed 清单，不阻塞其余条目；预留失败（库存仍不足）保持排队。
     */
    WaitlistPromotionResult promoteAvailable(Instant now);
}
