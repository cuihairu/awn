package site.awn.catalog.api;

public record CreateCategoryCommand(String categoryCode, String name, String description) {
}
