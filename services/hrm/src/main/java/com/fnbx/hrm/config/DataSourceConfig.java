package com.fnbx.hrm.config;

import com.fnbx.shared.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Thay DataSource auto-config cua Spring Boot bang Hikari BOC trong
 * {@link TenantAwareDataSource}, de moi transaction tu dat ngu canh tenant
 * ({@code set_config('app.business_id', ?, true)}) truoc cau lenh dau tien.
 * Khong co lop boc nay thi RLS khong co gi de loc -> {@code current_business_id()}
 * tra NULL -> moi query tenant tra ve 0 dong (fail-closed).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Hikari PHAI la bean rieng mang {@code @ConfigurationProperties("spring.datasource.hikari")}.
     * {@code DataSourceProperties.initializeDataSourceBuilder()} chi mang theo
     * url / username / password / driver — moi thu trong khoi
     * {@code spring.datasource.hikari.*} se BI BO QUA neu khong bind o day.
     *
     * <p>Hau qua that: mat {@code prepareThreshold: 0} thi pgjdbc tao server-side
     * prepared statement sau lan chay thu 5; PgBouncer transaction pooling tra
     * connection do cho client khac va bao
     * {@code prepared statement "S_3" already exists} — loi CHAP CHON, chi hien khi tai cao.
     */
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

    /**
     * The payroll engine needs one transaction per phase (calculate, then record
     * the run outcome), so a failed run's issues survive rolling back its figures
     * (spec section 6.2 step 12) - a single {@code @Transactional} method cannot
     * do that on itself.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
