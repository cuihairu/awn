package site.awn.intelligence.domain;

/** 情报来源不存在 */
public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(String message) {
        super(message);
    }
}
