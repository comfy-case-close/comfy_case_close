package com.fnbx.identity.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AuthInfrastructureConfiguration {
    @Bean
    public TransactionTemplate authTransactions(DataSource dataSource) {
        return new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

}
