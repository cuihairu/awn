package site.awn.catalog.api;

public record CreateProductCommand(String productCode, Long eventId, Long sessionId,
                            Long categoryId, String name, Long unitPrice) {
}
