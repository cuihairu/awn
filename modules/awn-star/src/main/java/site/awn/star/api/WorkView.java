package site.awn.star.api;

/** 作品视图。 */
public record WorkView(
        long id,
        String title,
        String type,
        Integer year,
        String description,
        String status) {
}
