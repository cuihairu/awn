package site.awn.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.notification.api.NotificationFacade;
import site.awn.notification.api.NotificationView;
import site.awn.notification.api.SendNotificationCommand;
import site.awn.notification.domain.Notification;
import site.awn.notification.domain.NotificationChannel;
import site.awn.notification.domain.NotificationDraft;
import site.awn.notification.domain.NotificationStatus;
import site.awn.notification.infrastructure.NotificationRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知用例实现。任何通道失败（未知通道、未接入、外部网关异常）都被记录为
 * FAILED 并返回，绝不向上抛出——通知失败不回滚已成功的订单。
 * 每次投递（成功或失败）都留痕，收件箱可查。
 */
@Service
public class NotificationFacadeImpl implements NotificationFacade {

    private static final Logger log = LoggerFactory.getLogger(NotificationFacadeImpl.class);

    private final NotificationRepository messages;
    private final Map<String, NotificationChannel> channelsByCode;

    public NotificationFacadeImpl(NotificationRepository messages, List<NotificationChannel> channels) {
        this.messages = messages;
        Map<String, NotificationChannel> index = new HashMap<>();
        for (NotificationChannel channel : channels) {
            index.put(channel.channel(), channel);
        }
        this.channelsByCode = Map.copyOf(index);
    }

    @Override
    @Transactional
    public NotificationView send(SendNotificationCommand command) {
        NotificationChannel channel = channelsByCode.get(command.channel());
        if (channel == null) {
            return toView(messages.save(Notification.failed(
                    command.recipientId(), command.channel(), command.eventType(),
                    command.title(), command.body(), command.reference(),
                    "unknown channel: " + command.channel())));
        }
        try {
            channel.send(new NotificationDraft(
                    command.recipientId(), command.channel(), command.eventType(),
                    command.title(), command.body(), command.reference()));
            return toView(messages.save(Notification.sent(
                    command.recipientId(), command.channel(), command.eventType(),
                    command.title(), command.body(), command.reference())));
        } catch (Exception e) {
            // 通道失败：留痕返回，不抛——通知失败不影响交易
            log.warn("notification channel failed: {} (reference={}): {}",
                    command.channel(), command.reference(), e.getMessage());
            return toView(messages.save(Notification.failed(
                    command.recipientId(), command.channel(), command.eventType(),
                    command.title(), command.body(), command.reference(), e.getMessage())));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationView> inboxOf(long recipientId) {
        return messages.findTop100ByRecipientIdAndStatusOrderByIdDesc(recipientId, NotificationStatus.SENT)
                .stream()
                .map(this::toView)
                .toList();
    }

    private NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getRecipientId(), n.getChannel(), n.getEventType(),
                n.getTitle(), n.getBody(), n.getReference(), n.getStatus().name(), n.getFailReason());
    }
}
