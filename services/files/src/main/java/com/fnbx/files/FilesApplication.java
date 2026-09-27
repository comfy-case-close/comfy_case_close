package com.fnbx.files;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * files-service — deployable doc lap, cong 8083.
 *
 * <p>{@code @EntityScan} liet ke ca entity cua domain khac ma service nay DOC
 * de JOIN xuyen schema. DOC duoc khong co nghia GHI duoc: DB role
 * {@code svc_files} chi co SELECT tren cac schema kia.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.config.WebConfig.class
})
@SpringBootApplication
@EntityScan(basePackages = {"com.fnbx.files.entity", "com.fnbx.identity.entity", "com.fnbx.platform.entity"})
@EnableJpaRepositories(basePackages = "com.fnbx.files.repository")
public class FilesApplication {
    public static void main(String[] args) {
        SpringApplication.run(FilesApplication.class, args);
    }
}
