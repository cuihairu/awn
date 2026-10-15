package site.awn.order.api;

/**
 * 观演人实名命令（一票一证，docs/design/prototypes/web/purchase.html 步骤 3）。
 * 姓名与证件号必填；证件号原文仅落库供实名校验，读面一律脱敏（{@link AttendeeView}）。
 */
public record AttendeeCommand(String name, String idNumber) {

    public AttendeeCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("attendee name is required");
        }
        name = name.strip();
        if (name.length() > 64) {
            throw new IllegalArgumentException("attendee name too long");
        }
        if (idNumber == null || idNumber.isBlank()) {
            throw new IllegalArgumentException("attendee idNumber is required");
        }
        idNumber = idNumber.strip();
        if (idNumber.length() > 32) {
            throw new IllegalArgumentException("attendee idNumber too long");
        }
    }
}
