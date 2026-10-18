package site.awn.worker;

import java.util.HashMap;
import java.util.Map;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Flyway 占位符注入。生产环境 worker 关闭 Flyway（迁移只由 awn-api 执行），
 * 但 worker 测试会开启 spring.flyway.enabled 跑迁移，任何执行迁移的进程都必须
 * 提供 V29__trading_role_password.sql 的占位符。
 *
 * <p>不能写在 application.yml 的 spring.flyway.placeholders 里——Spring 绑定 Map 值
 * 不解析 ${} 占位符，Flyway 会把整段当作未定义占位符报错，只能在代码里取环境变量。</p>
 */
@Configuration
public class FlywayPlaceholderConfig {

    private static final String TRADING_PASSWORD_ENV = "AWN_TRADING_DB_PASSWORD";
    private static final String TRADING_PASSWORD_DEFAULT = "change-me-trading-password";

    @Bean
    FlywayConfigurationCustomizer tradingRolePasswordPlaceholder(Environment environment) {
        return configuration -> {
            Map<String, String> placeholders =
                    new HashMap<>(configuration.getPlaceholders());
            placeholders.put("trading_role_password",
                    environment.getProperty(TRADING_PASSWORD_ENV, TRADING_PASSWORD_DEFAULT));
            configuration.placeholders(placeholders);
        };
    }
}
