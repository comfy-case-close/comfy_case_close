package com.fnbx.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * platform-service — deployable doc lap, cong 8082.
 *
 * <p>{@code @EntityScan} liet ke ca entity cua domain khac ma service nay DOC
 * de JOIN xuyen schema. DOC duoc khong co nghia GHI duoc: DB role
 * {@code svc_platform} chi co SELECT tren cac schema kia.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.config.WebConfig.class
})
@SpringBootApplication
@EntityScan(basePackages = {"com.fnbx.platform.entity", "com.fnbx.identity.entity"})
@EnableJpaRepositories(basePackages = "com.fnbx.platform.repository")
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
