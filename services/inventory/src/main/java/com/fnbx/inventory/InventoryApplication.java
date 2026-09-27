package com.fnbx.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * inventory-service — deployable doc lap, cong 8088.
 *
 * <p>{@code @EntityScan} liet ke ca entity cua domain khac ma service nay DOC
 * de JOIN xuyen schema. DOC duoc khong co nghia GHI duoc: DB role
 * {@code svc_inventory} chi co SELECT tren cac schema kia.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.config.WebConfig.class
})
@SpringBootApplication
@EntityScan(basePackages = {"com.fnbx.inventory.entity", "com.fnbx.identity.entity", "com.fnbx.platform.entity", "com.fnbx.files.entity"})
@EnableJpaRepositories(basePackages = "com.fnbx.inventory.repository")
public class InventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
