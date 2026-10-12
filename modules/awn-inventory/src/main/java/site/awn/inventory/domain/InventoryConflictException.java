package site.awn.inventory.domain;

/** 预留状态机非法迁移（如确认已释放的预留）。进入异常订单处理流程，不可静默重试。 */
public class InventoryConflictException extends RuntimeException {

    public InventoryConflictException(String message) {
        super(message);
    }
}
