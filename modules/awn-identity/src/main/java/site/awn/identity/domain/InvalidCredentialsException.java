package site.awn.identity.domain;

/** 凭据无效：未知邮箱与错误口令共用同一异常同一消息（不泄露账号存在性） */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid email or password");
    }
}
