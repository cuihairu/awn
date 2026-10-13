package site.awn.promotion.domain;

/** 券操作冲突：配额耗尽 / 窗口外 / 每人限领一张 / 重复核销 */
public class CouponConflictException extends RuntimeException {

    public CouponConflictException(String message) {
        super(message);
    }
}
