package site.awn.catalog.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 商品状态机（docs/architecture/trading-model.md G3）。
 * 创建即 DRAFT（Product 构造默认值），发布走领域守卫 publish()，不允许任意 API 直接改状态字符串。
 */
public enum ProductStatus {

    DRAFT,
    PUBLISHED,
    CANCELLED;

    private static final Map<ProductStatus, Set<ProductStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(PUBLISHED, CANCELLED),
            PUBLISHED, EnumSet.of(CANCELLED),
            CANCELLED, EnumSet.noneOf(ProductStatus.class));

    /** 终态：CANCELLED */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** 迁移合法性由本表唯一定义，任何更新路径都必须先经它守卫 */
    public boolean canTransitionTo(ProductStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
