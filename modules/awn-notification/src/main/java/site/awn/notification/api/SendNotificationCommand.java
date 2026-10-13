package site.awn.notification.api;

/** 发送通知：收件人、通道、事件类型、标题、正文、业务引用 */
public record SendNotificationCommand(
        long recipientId,
        String channel,
        String eventType,
        String title,
        String body,
        String reference) {

    public SendNotificationCommand {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
    }
}
