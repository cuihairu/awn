package site.awn.promotion.domain;

/** 券 / 模板不存在 */
public class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException(String message) {
        super(message);
    }
}
