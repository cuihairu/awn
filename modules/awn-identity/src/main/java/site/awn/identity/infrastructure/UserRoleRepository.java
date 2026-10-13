package site.awn.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.identity.domain.UserRole;
import site.awn.identity.domain.UserRoleId;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserIdOrderByRoleAsc(Long userId);

    boolean existsByUserIdAndRole(Long userId, String role);
}
