package site.awn.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * 运行时 JPA 单元拆分（方案与分期见 docs/design/dual-datasource-split-plan.md，此处实现 S1）。
 *
 * <p>交易单元 {@code entityManagerFactory} / {@code transactionManager} 映射全部交易实体，
 * 走主数据源；情报单元 {@code intelEntityManagerFactory} / {@code intelTransactionManager}
 * 只映射 {@code site.awn.intelligence} 下的 intelligence schema 实体，走情报数据源。
 * 情报源默认回退主源同值（本地 / 测试行为不变），部署收紧（S4）时指向受限账号
 * {@code awn_intel}；迁移则继续以 {@code spring.flyway.url} 指定的高权限账号执行。</p>
 *
 * <p>Boot 的自动装配对 {@code LocalContainerEntityManagerFactoryBean}、{@code TransactionManager}
 * 均带 {@code @ConditionalOnMissingBean} 回退，任一 {@code @EnableJpaRepositories} 存在又会整体
 * 关闭仓库自动装配，因此两个单元的 EMF / 事务管理器 / 仓库扫描器都在这里显式声明。
 * 交易单元的实体排除必须写在 EMF 上（Boot 的 {@code @EntityScan} 只取包名，不生效 excludeFilters），
 * 仓库扫描器另加排除以免两侧都注册情报仓库。</p>
 */
@Configuration
public class PersistenceConfig {

    private static final String DDL_VALIDATE = "validate";

    /** 交易实体扫描根包（两个应用的主类包） */
    private static final String TRADING_PACKAGES = "site.awn";

    /** 情报实体包，全部映射 intelligence schema */
    private static final String INTELLIGENCE_PACKAGES = "site.awn.intelligence";

    private static final String INTELLIGENCE_EXCLUDE_PATTERN = INTELLIGENCE_PACKAGES + "\\..*";

    /** 交易仓库：扫描全部模块，排除情报仓库（避免与情报扫描器重复注册）。 */
    @Configuration
    @EnableJpaRepositories(
            basePackages = TRADING_PACKAGES,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = INTELLIGENCE_EXCLUDE_PATTERN))
    static class TradingJpaRepositories {
    }

    /** 情报仓库：只扫描情报模块，绑定情报单元。 */
    @Configuration
    @EnableJpaRepositories(
            basePackages = INTELLIGENCE_PACKAGES,
            entityManagerFactoryRef = "intelEntityManagerFactory",
            transactionManagerRef = "intelTransactionManager")
    static class IntelJpaRepositories {
    }

    /** 交易数据源（主源）：沿用 {@code spring.datasource.*} 配置与连接池设置。 */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    DataSourceProperties tradingDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    HikariDataSource tradingDataSource(
            @Qualifier("tradingDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * 情报数据源：独立连接配置（{@code spring.intel-datasource.*}，Hikari 平铺属性）。
     * 默认回退主源同值，S4 部署收紧时才指向受限账号 {@code awn_intel}。
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.intel-datasource")
    HikariDataSource intelDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /** 交易单元 EntityManagerFactory：扫描 {@code site.awn} 并排除 intelligence。 */
    @Bean
    @Primary
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder, DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = builder
                .dataSource(dataSource)
                .properties(Map.of("hibernate.ddl-auto", DDL_VALIDATE))
                .build();
        emf.setPackagesToScan(TRADING_PACKAGES);
        // Spring 7 没有 @EntityScan 的 excludeFilters 通道（Boot 只取包名），
        // 这里用 ORM 的托管类名过滤器把 intelligence 实体挡在交易单元之外。
        emf.setManagedClassNameFilter(
                className -> !className.startsWith(INTELLIGENCE_PACKAGES + "."));
        return emf;
    }

    @Bean
    @Primary
    JpaTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory,
            ObjectProvider<TransactionManagerCustomizers> customizers) {
        return withCustomizers(new JpaTransactionManager(entityManagerFactory), customizers);
    }

    /** 情报单元 EntityManagerFactory：只映射 intelligence schema 实体，走情报数据源。 */
    @Bean
    LocalContainerEntityManagerFactoryBean intelEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("intelDataSource") DataSource intelDataSource) {
        return builder
                .dataSource(intelDataSource)
                .packages(INTELLIGENCE_PACKAGES)
                .properties(Map.of("hibernate.ddl-auto", DDL_VALIDATE))
                .build();
    }

    @Bean
    JpaTransactionManager intelTransactionManager(
            @Qualifier("intelEntityManagerFactory") EntityManagerFactory intelEntityManagerFactory,
            ObjectProvider<TransactionManagerCustomizers> customizers) {
        return withCustomizers(new JpaTransactionManager(intelEntityManagerFactory), customizers);
    }

    private static JpaTransactionManager withCustomizers(JpaTransactionManager manager,
                                                          ObjectProvider<TransactionManagerCustomizers> customizers) {
        TransactionManagerCustomizers applied = customizers.getIfAvailable();
        if (applied != null) {
            applied.customize(manager);
        }
        return manager;
    }
}
