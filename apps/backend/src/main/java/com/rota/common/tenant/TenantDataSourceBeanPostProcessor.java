package com.rota.common.tenant;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Wraps the application's autoconfigured {@link DataSource} bean in a
 * {@link TenantAwareDataSource}, preserving all of Spring Boot's Hikari tuning while
 * adding per-connection tenant scoping.
 *
 * <p>Only the primary runtime datasource bean is a {@code DataSource} bean here; Flyway
 * runs on its own privileged connection configured via {@code spring.flyway.*} and is
 * therefore NOT wrapped (so migrations keep running as the privileged role).
 */
@Component
public class TenantDataSourceBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(dataSource);
        }
        return bean;
    }
}
