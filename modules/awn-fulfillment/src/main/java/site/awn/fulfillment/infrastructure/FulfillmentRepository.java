package site.awn.fulfillment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.fulfillment.domain.Fulfillment;

import java.util.Optional;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, Long> {

    Optional<Fulfillment> findByFulfillmentNo(String fulfillmentNo);

    Optional<Fulfillment> findByOrderNo(String orderNo);
}
