package site.awn.event.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.event.domain.Event;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventCode(String eventCode);
}
