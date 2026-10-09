package com.fnbx.hrm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("hrm.payslip-confirmation")
public record PayslipConfirmationProperties(
        @DefaultValue("30") int tokenTtlDays,
        @DefaultValue("http://localhost:3000/confirm-payslip") String publicUrl) {
}
