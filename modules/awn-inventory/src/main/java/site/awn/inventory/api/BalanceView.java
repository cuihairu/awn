package site.awn.inventory.api;

public record BalanceView(
        String poolCode,
        int totalQuantity,
        int reservedQuantity,
        int soldQuantity,
        int available,
        long version
) {
}
