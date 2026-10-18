package site.awn.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.Transactional;
import site.awn.intelligence.application.IntelligenceFacadeImpl;
import site.awn.intelligence.domain.DataSource;
import site.awn.intelligence.domain.NormalizedObservation;
import site.awn.intelligence.domain.PriceHistoryEntry;
import site.awn.intelligence.domain.RawObservation;
import site.awn.order.domain.Order;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行时 JPA 单元拆分验收（设计 docs/design/dual-datasource-split-plan.md S1）。
 *
 * <p>验收口径：交易单元（entityManagerFactory / transactionManager）只映射交易实体，
 * 情报单元（intelEntityManagerFactory / intelTransactionManager）只映射 intelligence schema
 * 的四个实体；情报服务的事务注解绑定到情报单元的事务管理器。</p>
 */
@SpringBootTest
class PersistenceUnitSplitTests extends AbstractPostgresIntegrationTest {

    private static final Set<Class<?>> INTELLIGENCE_ENTITIES = Set.of(
            RawObservation.class, DataSource.class, NormalizedObservation.class, PriceHistoryEntry.class);

    @Autowired
    @Qualifier("entityManagerFactory")
    LocalContainerEntityManagerFactoryBean tradingEmf;

    @Autowired
    @Qualifier("intelEntityManagerFactory")
    LocalContainerEntityManagerFactoryBean intelEmf;

    @Autowired
    @Qualifier("intelTransactionManager")
    JpaTransactionManager intelTransactionManager;

    private static Set<Class<?>> entityClasses(EntityManagerFactory emf) {
        return emf.getMetamodel().getEntities().stream()
                .map(e -> e.getJavaType())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    void tradingUnitMapsNoIntelligenceEntities() {
        Set<Class<?>> entities = entityClasses(tradingEmf.getObject());
        assertThat(entities)
                .as("交易单元必须排除 intelligence schema 实体")
                .doesNotContainAnyElementsOf(INTELLIGENCE_ENTITIES);
        assertThat(entities)
                .as("交易单元仍映射交易实体")
                .contains(Order.class);
    }

    @Test
    void intelUnitMapsOnlyIntelligenceEntities() {
        assertThat(entityClasses(intelEmf.getObject()))
                .as("情报单元恰好映射 intelligence schema 的四个实体")
                .isEqualTo(INTELLIGENCE_ENTITIES);
    }

    @Test
    void intelTransactionManagerIsBoundToIntelUnit() {
        assertThat(entityClasses(intelTransactionManager.getEntityManagerFactory()))
                .as("情报事务管理器必须挂在情报单元上")
                .isEqualTo(INTELLIGENCE_ENTITIES);
    }

    @Test
    void intelligenceFacadeTransactionsUseIntelUnit() {
        Arrays.stream(IntelligenceFacadeImpl.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Transactional.class))
                .forEach(m -> assertThat(m.getAnnotation(Transactional.class).value())
                        .as("情报事务必须走 intelTransactionManager: " + m.getName())
                        .isEqualTo("intelTransactionManager"));
    }
}
