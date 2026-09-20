package com.fnbx.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;

/**
 * identity-service — deployable doc lap, cong 8081.
 *
 * <p>Authentication uses JDBC repositories for explicit row locks and atomic
 * rotation inserts. JPA is not initialized by this service; shared entity
 * libraries remain available to the other services unchanged.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.config.WebConfig.class
})
@SpringBootApplication(exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
public class IdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
