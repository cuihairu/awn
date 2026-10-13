package site.awn.notification.domain;

/**
 * 通知通道端口：站内信 / 邮件 / 短信等适配实现本接口。
 * 通道实现失败时抛异常，由用例层记录 FAILED——通知失败不回滚订单。
 */
public interface NotificationChannel {

    /** 通道编码（INBOX / EMAIL / SMS），与 SendNotificationCommand.channel 对应 */
    String channel();

    /** 投递一条通知；失败抛异常 */
    void send(NotificationDraft draft);
}
