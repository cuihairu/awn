package site.awn.star.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.star.domain.Work;

import java.util.List;

public interface WorkRepository extends JpaRepository<Work, Long> {

    List<Work> findByStatusOrderByIdDesc(String status);

    List<Work> findByStatusAndTypeOrderByIdDesc(String status, String type);
}
