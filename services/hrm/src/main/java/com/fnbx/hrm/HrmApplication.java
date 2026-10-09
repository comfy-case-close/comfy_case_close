package com.fnbx.hrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.security.PermissionConfiguration.class,
        com.fnbx.hrm.config.HrmWebConfig.class,
        com.fnbx.mail.MailConfiguration.class
})
@SpringBootApplication
@EntityScan(basePackages = {"com.fnbx.hrm.entity", "com.fnbx.identity.entity"})
@EnableJpaRepositories(basePackages = "com.fnbx.hrm.repository")
public class HrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(HrmApplication.class, args);
    }
}
