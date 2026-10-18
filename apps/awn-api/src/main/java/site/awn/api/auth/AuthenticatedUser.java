package site.awn.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static long userId(HttpServletRequest request) {
        Object value = request.getAttribute(ApiAuthenticationFilter.USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) return userId;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }

    public static void requireOwner(long expectedUserId, long actualUserId) {
        if (expectedUserId != actualUserId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "resource belongs to another user");
        }
    }

}
