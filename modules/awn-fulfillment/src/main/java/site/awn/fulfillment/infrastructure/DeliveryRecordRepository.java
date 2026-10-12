package site.awn.fulfillment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.fulfillment.domain.DeliveryRecord;

import java.util.List;
import java.util.Optional;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, Long> {

    List<DeliveryRecord> findByFulfillmentNoOrderByDeliveredAtAsc(String fulfillmentNo);

    Optional<DeliveryRecord> findFirstByFulfillmentNoAndResultOrderByDeliveredAtAsc(
            String fulfillmentNo, site.awn.fulfillment.domain.DeliveryResult result);
}
