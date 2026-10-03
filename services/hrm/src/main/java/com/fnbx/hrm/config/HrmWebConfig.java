package com.fnbx.hrm.config;

import com.fnbx.shared.exception.GlobalExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Every hrm endpoint lives under {@code /api/v1/payroll}; the gateway routes that prefix to this service. */
@Configuration(proxyBeanMethods = false)
@Import(GlobalExceptionHandler.class)
public class HrmWebConfig implements WebMvcConfigurer {

    private static final String API_PREFIX = "/api/v1/payroll";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage("com.fnbx.hrm"));
    }
}
