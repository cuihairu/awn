package site.awn.notification.domain;

/** 通知草稿：通道投递的输入（收件人、事件、标题、正文、业务引用） */
public record NotificationDraft(
        long recipientId,
        String channel,
        String eventType,
        String title,
        String body,
        String reference) {
}
