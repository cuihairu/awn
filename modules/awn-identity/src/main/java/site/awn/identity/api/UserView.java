package site.awn.identity.api;

import java.util.List;

/** 用户视图（不含口令材料） */
public record UserView(
        long id,
        String email,
        String displayName,
        String status,
        List<String> roles) {
}
