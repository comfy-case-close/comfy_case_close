package com.fnbx.cashclose.config;

import com.fnbx.shared.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Boc DataSource that bang {@link TenantAwareDataSource} de moi connection
 * deu mang ngu canh tenant xuong PostgreSQL cho RLS.
 *
 * <p>Xem javadoc cua {@code TenantAwareDataSource} de hieu vi sao tham so thu
 * ba cua {@code set_config} phai la {@code true}.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource hikariDataSource(
            @Qualifier("dataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("hikariDataSource") HikariDataSource real) {
        return new TenantAwareDataSource(real);
    }
}
