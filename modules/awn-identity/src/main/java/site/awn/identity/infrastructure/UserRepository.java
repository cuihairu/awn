package site.awn.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.identity.domain.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
