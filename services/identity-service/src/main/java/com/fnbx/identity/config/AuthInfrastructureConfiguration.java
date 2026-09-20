package com.fnbx.identity.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableAsync
public class AuthInfrastructureConfiguration {
    @Bean
    public TransactionTemplate authTransactions(DataSource dataSource) {
        return new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @Bean(name = "emailTaskExecutor")
    public ThreadPoolTaskExecutor emailTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(4); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("identity-mail-");
        return executor;
    }
}
