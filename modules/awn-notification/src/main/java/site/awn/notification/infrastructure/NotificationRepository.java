package site.awn.notification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.notification.domain.Notification;
import site.awn.notification.domain.NotificationStatus;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 收件箱：指定收件人最新 100 条已送达消息；FAILED 留痕不在买家收件箱 */
    List<Notification> findTop100ByRecipientIdAndStatusOrderByIdDesc(Long recipientId, NotificationStatus status);
}
