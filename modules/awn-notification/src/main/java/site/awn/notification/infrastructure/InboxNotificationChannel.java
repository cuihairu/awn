package site.awn.notification.infrastructure;

import org.springframework.stereotype.Component;
import site.awn.notification.domain.NotificationChannel;
import site.awn.notification.domain.NotificationDraft;

/**
 * 站内信通道：投递即通知表落库（用例层统一留痕），收件人可在收件箱中查到。
 */
@Component
public class InboxNotificationChannel implements NotificationChannel {

    @Override
    public String channel() {
        return "INBOX";
    }

    @Override
    public void send(NotificationDraft draft) {
        // 站内信无需外部调用：用例层落库即为送达
    }
}
