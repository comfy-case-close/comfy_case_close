package com.fnbx.cashclose;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * cashclose-service — deployable doc lap, cong 8086.
 *
 * <h2>Vi sao can @EntityScan liet ke nhieu package</h2>
 * Service nay DOC entity cua identity / platform / files / integration de
 * JOIN xuyen schema. Do la dac trung cua Service-Based (sach Ch.13 tr.164):
 * dung chung database cho phep JOIN truc tiep thay vi goi HTTP.
 *
 * <p>DOC duoc khong co nghia la GHI duoc: DB role {@code svc_cashclose} chi
 * co {@code SELECT} tren cac schema kia. Thu {@code UPDATE identity.branch}
 * se nhan {@code ERROR: permission denied for table branch}.
 *
 * <p>{@code @EnableJpaRepositories} thi CHI quet package cua chinh minh —
 * de khong vo tinh nap repository ghi cua domain khac.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.security.PermissionConfiguration.class,
        com.fnbx.shared.config.WebConfig.class,
        com.fnbx.mail.MailConfiguration.class
})
@SpringBootApplication
@EntityScan(basePackages = {
        "com.fnbx.cashclose.entity",
        "com.fnbx.identity.entity",      // read: branch, staff, shift_type
        "com.fnbx.platform.entity",      // read: denomination, movement_kind (SCD-2)
        "com.fnbx.files.entity",         // read: stored_file
        "com.fnbx.integration.entity"    // read: shift_sales <- expected revenue
})
@EnableJpaRepositories(basePackages = "com.fnbx.cashclose.repository")
public class CashCloseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CashCloseApplication.class, args);
    }
}
