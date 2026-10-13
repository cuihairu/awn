package site.awn.identity.domain;

/** 用户冲突：邮箱已被注册 */
public class UserConflictException extends RuntimeException {

    public UserConflictException(String message) {
        super(message);
    }
}
