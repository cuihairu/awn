package site.awn.star.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.star.domain.PersonWork;

import java.util.List;

public interface PersonWorkRepository extends JpaRepository<PersonWork, Long> {

    List<PersonWork> findByPersonId(Long personId);

    List<PersonWork> findByWorkId(Long workId);

    boolean existsByPersonIdAndWorkIdAndRoleTypeAndRoleName(
            Long personId, Long workId, String roleType, String roleName);
}
