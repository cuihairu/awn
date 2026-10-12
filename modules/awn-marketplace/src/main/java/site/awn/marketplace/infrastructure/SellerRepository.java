package site.awn.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.marketplace.domain.Seller;

import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    Optional<Seller> findBySellerNo(String sellerNo);
}
