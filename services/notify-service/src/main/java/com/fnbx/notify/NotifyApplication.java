package com.fnbx.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * notify-service — deployable doc lap, cong 8084.
 *
 * <p>{@code @EntityScan} liet ke ca entity cua domain khac ma service nay DOC
 * de JOIN xuyen schema. DOC duoc khong co nghia GHI duoc: DB role
 * {@code svc_notify} chi co SELECT tren cac schema kia.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.config.WebConfig.class
})
@SpringBootApplication
@EntityScan(basePackages = {"com.fnbx.notify.entity", "com.fnbx.identity.entity", "com.fnbx.platform.entity"})
@EnableJpaRepositories(basePackages = "com.fnbx.notify.repository")
public class NotifyApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotifyApplication.class, args);
    }
}
