package site.awn.order.api;

import java.util.List;

/**
 * 争议模块对外接口（docs/architecture/dispute-handling.md）。
 * 权限模型：买家面（开单 / 撤回 / 查自己的争议）与平台管理面（受理 / 裁决）分开；
 * 裁决权限收敛在 admin 组合根（X-Admin-Token），模块层不重复做权限判断，
 * 但状态机守卫保证即使误调用也改不了状态。
 */
public interface DisputeFacade {

    /** 买家开争议：仅订单本人、订单 PAID/FULFILLING/FULFILLED、同订单无在途争议 */
    DisputeView openDispute(OpenDisputeCommand command);

    /** 订单的争议列表（买家面，服务端校验订单归属） */
    List<DisputeView> disputesOfOrder(String orderNo, Long userId);

    /** 运营后台争议视图：最近争议（新→旧，限 50 条） */
    List<DisputeView> recentDisputes();

    /** 买家撤回：仅开单本人、仅 OPEN（已受理只能由平台裁决关闭） */
    DisputeView withdraw(String disputeNo, Long userId);

    /** 平台受理：OPEN → UNDER_REVIEW。重复受理抛冲突。 */
    DisputeView accept(String disputeNo);

    /** 平台裁决：UNDER_REVIEW → RESOLVED，结论与 note 必填，留痕 decidedBy/decidedAt */
    DisputeView adjudicate(AdjudicateDisputeCommand command);
}
