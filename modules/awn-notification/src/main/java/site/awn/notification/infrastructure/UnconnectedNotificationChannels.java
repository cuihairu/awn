package site.awn.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import site.awn.notification.domain.NotificationChannel;
import site.awn.notification.domain.NotificationDraft;

/**
 * 邮件 / 短信通道：适配位已就绪，发送通道未接入。
 * 投递尝试抛异常，由用例层记录 FAILED——通知失败不回滚订单。
 */
public class UnconnectedNotificationChannels {

    private static final Logger log = LoggerFactory.getLogger(UnconnectedNotificationChannels.class);

    private UnconnectedNotificationChannels() {
    }

    static void notConnected(String channel, NotificationDraft draft) {
        log.warn("notification channel not connected: {} (eventType={}, reference={})",
                channel, draft.eventType(), draft.reference());
        throw new IllegalStateException(channel + " channel not connected");
    }

    @Component
    static class EmailNotificationChannel implements NotificationChannel {

        @Override
        public String channel() {
            return "EMAIL";
        }

        @Override
        public void send(NotificationDraft draft) {
            notConnected("EMAIL", draft);
        }
    }

    @Component
    static class SmsNotificationChannel implements NotificationChannel {

        @Override
        public String channel() {
            return "SMS";
        }

        @Override
        public void send(NotificationDraft draft) {
            notConnected("SMS", draft);
        }
    }
}
