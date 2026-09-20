package com.fnbx.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * integration-service — deployable doc lap, cong 8085.
 *
 * <p>{@code @EntityScan} liet ke ca entity cua domain khac ma service nay DOC
 * de JOIN xuyen schema. DOC duoc khong co nghia GHI duoc: DB role
 * {@code svc_integration} chi co SELECT tren cac schema kia.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.config.WebConfig.class
})
@SpringBootApplication
@EntityScan(basePackages = {"com.fnbx.integration.entity", "com.fnbx.identity.entity", "com.fnbx.platform.entity", "com.fnbx.files.entity"})
@EnableJpaRepositories(basePackages = "com.fnbx.integration.repository")
public class IntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }
}
