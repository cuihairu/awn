package site.awn.notification.api;

import java.util.List;

/**
 * 通知模块对外接口。其他模块只允许通过本接口发送通知。
 * 投递失败不抛异常：结果以 FAILED 状态返回——通知失败不回滚已成功的订单。
 */
public interface NotificationFacade {

    /**
     * 发送一条通知。未知通道或通道投递失败时返回 FAILED 视图（不抛出），
     * 失败留痕供运营排查。
     */
    NotificationView send(SendNotificationCommand command);

    /** 收件箱：指定收件人最新 100 条已送达消息（FAILED 留痕不进买家收件箱） */
    List<NotificationView> inboxOf(long recipientId);
}
