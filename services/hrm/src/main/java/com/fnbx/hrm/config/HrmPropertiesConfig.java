package com.fnbx.hrm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({HrmContractProperties.class, PayslipConfirmationProperties.class})
public class HrmPropertiesConfig {
}
