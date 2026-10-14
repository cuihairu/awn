package site.awn.star.domain;

/** 人物或作品不存在，或未发布不可公开（公开面一律 404，不泄露草稿）。 */
public class PersonNotFoundException extends RuntimeException {

    public PersonNotFoundException(long personId) {
        super("person not found: " + personId);
    }
}
