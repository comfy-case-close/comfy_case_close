package com.fnbx.hrm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("hrm.contract")
public record HrmContractProperties(@DefaultValue("30") int expiryWarningDays, @DefaultValue("500") int importMaxRows,
        @DefaultValue("47 Đường số 10, Khu phố 63, Phường Hiệp Bình, Thành phố Hồ Chí Minh, Việt Nam") String headOfficeAddress) {
}
