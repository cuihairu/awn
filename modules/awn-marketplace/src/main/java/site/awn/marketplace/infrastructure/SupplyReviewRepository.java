package site.awn.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.marketplace.domain.SupplyReview;

import java.util.Optional;

public interface SupplyReviewRepository extends JpaRepository<SupplyReview, Long> {

    Optional<SupplyReview> findByReviewNo(String reviewNo);
}
