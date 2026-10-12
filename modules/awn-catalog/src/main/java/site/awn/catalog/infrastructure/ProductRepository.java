package site.awn.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.catalog.domain.Product;
import site.awn.catalog.domain.ProductStatus;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductCode(String productCode);

    List<Product> findByEventId(Long eventId);

    @Query("SELECT p FROM Product p WHERE p.sessionId = :sessionId AND p.status = :status")
    List<Product> findPublishedBySession(@Param("sessionId") Long sessionId,
                                         @Param("status") ProductStatus status);
}
