package site.awn.star.domain;

/** 作品不存在，或未发布不可公开（公开面一律 404，不泄露草稿）。 */
public class WorkNotFoundException extends RuntimeException {

    public WorkNotFoundException(long workId) {
        super("work not found: " + workId);
    }
}
