package site.awn.event.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.event.domain.Session;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findBySessionCode(String sessionCode);

    List<Session> findByEventId(Long eventId);
}
