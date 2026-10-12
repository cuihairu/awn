package site.awn.inventory.domain;

/** 原子条件 UPDATE 未命中（库存不足）。对外映射为可重试的业务失败，不是系统错误。 */
public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(String poolCode) {
        super("insufficient inventory for pool: " + poolCode);
    }
}
