package com.fnbx.shared.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration(after = ServletPayloadLoggingAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "fnb.logging.request", name = "enabled", matchIfMissing = true)
public class RequestLoggingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RequestLoggingFilter.class)
    public RequestLoggingFilter requestLoggingFilter(
            @Value("${spring.application.name:application}") String serviceName,
            org.springframework.beans.factory.ObjectProvider<ServletPayloadLogging> payloadLogging) {
        return new RequestLoggingFilter(serviceName, payloadLogging.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(name = "requestLoggingFilterRegistration")
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(RequestLoggingFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
