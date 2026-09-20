package com.fnbx.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.fnbx.shared.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Shared servlet API prefix; controllers declare resource-relative mappings. */
@Configuration(proxyBeanMethods = false)
@Import(GlobalExceptionHandler.class)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Limit this to FNB REST controllers; leave Spring's and springdoc's routes intact.
        configurer.addPathPrefix("/api/v1",
                HandlerTypePredicate.forAnnotation(RestController.class)
                        .and(HandlerTypePredicate.forBasePackage("com.fnbx")));
    }
}
