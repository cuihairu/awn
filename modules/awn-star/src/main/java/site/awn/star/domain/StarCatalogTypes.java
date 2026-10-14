package site.awn.star.domain;

import java.util.Set;

/**
 * 图鉴枚举白名单（首版口径，用户 2026-10-14 拍板：可扩展）。
 * 扩展方式：在集合中追加编码；编码与展示文案的映射由前端维护。
 */
public final class StarCatalogTypes {

    /** 人物职业类型 */
    public static final Set<String> PROFESSIONS = Set.of(
            "actor", "singer", "host", "director", "musician",
            "producer", "screenwriter", "dancer", "model");

    /** 作品类型 */
    public static final Set<String> WORK_TYPES = Set.of(
            "movie", "tv", "variety", "song", "album", "concert", "stage");

    /** 人物在作品中的身份 */
    public static final Set<String> ROLE_TYPES = Set.of(
            "cast_lead", "cast_supporting", "cast_cameo",
            "director", "screenwriter", "producer",
            "composer", "lyricist", "theme_song", "voice");

    private StarCatalogTypes() {
    }
}
