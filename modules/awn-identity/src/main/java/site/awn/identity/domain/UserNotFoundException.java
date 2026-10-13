package site.awn.identity.domain;

/** 用户不存在 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
