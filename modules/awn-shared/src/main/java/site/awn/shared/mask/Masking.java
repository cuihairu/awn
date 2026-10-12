package site.awn.shared.mask;

/**
 * 日志脱敏：手机号、证件号、邮箱在进入日志前统一遮蔽。
 * 规则只在此一处定义，日志代码不得自行拼星号。
 */
public final class Masking {

    private Masking() {
    }

    /** 手机号：138****1234（保留前 3 后 4）；短值整体遮蔽，避免泄漏片段 */
    public static String phone(String phone) {
        if (phone == null) {
            return "";
        }
        if (phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 身份证：1101**********12（保留前 4 后 2）；短值整体遮蔽 */
    public static String idCard(String idCard) {
        if (idCard == null) {
            return "";
        }
        if (idCard.length() < 6) {
            return "****";
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 2);
    }

    /** 邮箱：ab***@example.com（保留前 2 位与域名） */
    public static String email(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return keepOrEmpty(email);
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    /** 任意编号类字段：保留后 4 位，其余遮蔽（短值整体遮蔽） */
    public static String reference(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private static String keepOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
