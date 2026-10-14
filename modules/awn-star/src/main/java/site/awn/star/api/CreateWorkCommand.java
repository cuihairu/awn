package site.awn.star.api;

/** 创建作品。 */
public record CreateWorkCommand(String title, String type, Integer year, String description) {
}
