package site.awn.order.api;

/**
 * 观演人实名视图。证件号一律脱敏（留前 3 后 4，长度不足 8 全打码）——
 * 契约 docs/api/contracts.md「观演人实名」：证件号仅用于实名校验，展示时脱敏。
 */
public record AttendeeView(String name, String idNumber) {

    public static AttendeeView of(String name, String idNumber) {
        return new AttendeeView(name, mask(idNumber));
    }

    static String mask(String idNumber) {
        int length = idNumber.length();
        if (length <= 7) {
            return "*".repeat(length);
        }
        return idNumber.substring(0, 3) + "*".repeat(length - 7) + idNumber.substring(length - 4);
    }
}
