package site.awn.api.auth;

public class InvalidApiTokenException extends RuntimeException {
    public InvalidApiTokenException() {
        super("bearer token is missing, invalid, or expired");
    }
}
