package site.awn.catalog.domain;

/** 商品不存在（按商品编码查询未命中）。 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productCode) {
        super("product not found: " + productCode);
    }
}
