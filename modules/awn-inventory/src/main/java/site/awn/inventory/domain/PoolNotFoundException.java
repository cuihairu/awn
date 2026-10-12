package site.awn.inventory.domain;

/** 库存池不存在。 */
public class PoolNotFoundException extends RuntimeException {

    public PoolNotFoundException(String poolCode) {
        super("inventory pool not found: " + poolCode);
    }
}
