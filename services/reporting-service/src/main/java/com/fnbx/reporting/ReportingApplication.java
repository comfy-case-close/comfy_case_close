package com.fnbx.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * reporting-service — READ-SIDE, cong 8089.
 *
 * <h2>NGOAI LE DA GHI THANH VAN: khong co tang `service`</h2>
 *
 * Sach Ch.10 tr.132 canh bao <i>Architecture Sinkhole anti-pattern</i>:
 * <blockquote>
 * "request passes through layers as simple pass-through processing with
 * little or no logic performed"
 * </blockquote>
 * kem quy tac 80/20: ~20% request pass-through la binh thuong; <b>80%
 * pass-through nghia la layered la lua chon sai</b>.
 *
 * <p>reporting-service la <b>~100% pass-through</b>: doc, gom, tra ve. Tao
 * {@code ReportService} chi de chuyen tiep loi goi tu controller xuong
 * repository dung la sinkhole ma sach mo ta.
 *
 * <p>Vi vay: controller goi thang {@code JdbcTemplate} voi SQL viet tay.
 * Luat ArchUnit {@code controllerMustNotTouchRepository} duoc TAT cho service
 * nay qua {@code LayeredArchitectureRules.forReadOnlyService(...)}.
 *
 * <p>Khong dung JPA: truy van bao cao la aggregate xuyen 8 schema, viet SQL
 * truc tiep vua nhanh hon vua de doc hon JPQL.
 */
@org.springframework.context.annotation.Import({
        com.fnbx.shared.security.ServletSecurityConfiguration.class,
        com.fnbx.shared.config.WebConfig.class
})
@SpringBootApplication
public class ReportingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportingApplication.class, args);
    }
}
