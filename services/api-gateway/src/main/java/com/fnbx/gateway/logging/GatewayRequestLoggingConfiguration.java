package com.fnbx.gateway.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "fnb.logging.request", name = "enabled", matchIfMissing = true)
public class GatewayRequestLoggingConfiguration {
    @Bean
    @ConditionalOnMissingBean(GatewayRequestLoggingFilter.class)
    public GatewayRequestLoggingFilter gatewayRequestLoggingFilter(
            @Value("${spring.application.name:api-gateway}") String serviceName) {
        return new GatewayRequestLoggingFilter(serviceName);
    }
}
