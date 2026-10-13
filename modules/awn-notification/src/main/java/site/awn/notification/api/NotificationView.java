package site.awn.notification.api;

/** 通知投递结果视图（SENT / FAILED + 失败原因） */
public record NotificationView(
        Long id,
        long recipientId,
        String channel,
        String eventType,
        String title,
        String body,
        String reference,
        String status,
        String failReason) {
}
