package site.awn.event.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.event.domain.Event;
import site.awn.event.domain.EventStatus;

import java.util.Collection;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventCode(String eventCode);

    /** 公开读：仅给定状态集合的活动，按开始时间倒序 */
    java.util.List<Event> findByStatusInOrderByStartTimeDesc(Collection<EventStatus> statuses);
}
