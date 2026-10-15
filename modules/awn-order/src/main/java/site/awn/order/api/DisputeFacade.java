package site.awn.order.api;

import java.util.List;

/**
 * 争议模块对外接口（docs/architecture/dispute-handling.md）。
 * 权限模型：买家面（开单 / 撤回 / 查自己的争议）、卖家面（查归属争议 / 提交申辩）
 * 与平台管理面（受理 / 裁决 / 读申辩）分开；裁决权限收敛在 admin 组合根（X-Admin-Token），
 * 模块层不重复做权限判断，但状态机守卫保证即使误调用也改不了状态。
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

    /** 卖家争议列表：按开单时快照的挂牌卖家归属（新→旧，限 50 条）；未知卖家抛 NotFound */
    List<DisputeView> disputesOfSeller(String sellerNo);

    /** 卖家申辩：仅争议快照卖家、仅在途争议（OPEN/UNDER_REVIEW）可提交，追加留痕 */
    DisputeSellerResponseView submitSellerResponse(SubmitSellerResponseCommand command);

    /** 运营读某争议的卖家申辩（裁决依据，提交时间正序）；争议未知抛 NotFound */
    List<DisputeSellerResponseView> sellerResponsesOf(String disputeNo);

    /** 平台受理：OPEN → UNDER_REVIEW。重复受理抛冲突。 */
    DisputeView accept(String disputeNo);

    /** 平台裁决：UNDER_REVIEW → RESOLVED，结论与 note 必填，留痕 decidedBy/decidedAt */
    DisputeView adjudicate(AdjudicateDisputeCommand command);
}
