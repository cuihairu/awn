package site.awn.identity.api;

/** 注册：邮箱、明文口令（仅入参，落库前哈希）、显示名（可空） */
public record RegisterCommand(String email, String password, String displayName) {
}
