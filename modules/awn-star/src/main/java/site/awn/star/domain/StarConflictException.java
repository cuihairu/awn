package site.awn.star.domain;

/** 图鉴写操作冲突：重复绑定、非法状态迁移、重复发布。 */
public class StarConflictException extends RuntimeException {

    public StarConflictException(String message) {
        super(message);
    }
}
