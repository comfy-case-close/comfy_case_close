package com.fnbx.hrm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("hrm.contract")
public record HrmContractProperties(@DefaultValue("30") int expiryWarningDays, @DefaultValue("500") int importMaxRows) {
}
