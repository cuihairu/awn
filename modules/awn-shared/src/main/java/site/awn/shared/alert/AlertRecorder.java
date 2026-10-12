package site.awn.shared.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 运营告警：异常发生即落库（告警不因主流程事务回滚而丢失——独立事务提交）。
 * 库存异常 / 支付异常 / 对账差异三类由各模块在发现点触发。
 */
@Service
public class AlertRecorder {

    private static final Logger log = LoggerFactory.getLogger(AlertRecorder.class);

    private final AlertRepository alerts;

    public AlertRecorder(AlertRepository alerts) {
        this.alerts = alerts;
    }

    /** 告警必须独立于调用方事务：即使主流程回滚，异常本身也要留痕 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void raise(AlertCategory category, AlertSeverity severity, String message, String reference) {
        log.warn("ALERT [{}] {}: {} ({})", category, severity, message, reference);
        alerts.save(new AlertRecord(category, severity, message, reference));
    }

    @Transactional(readOnly = true)
    public List<AlertRecord> openAlerts() {
        return alerts.findTop100ByAcknowledgedFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AlertRecord> recentAlerts() {
        return alerts.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional
    public boolean acknowledge(long id) {
        return alerts.findById(id).map(record -> {
            record.acknowledge();
            return true;
        }).orElse(false);
    }
}
