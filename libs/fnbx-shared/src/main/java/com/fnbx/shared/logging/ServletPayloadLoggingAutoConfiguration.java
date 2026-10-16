package com.fnbx.shared.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@AutoConfiguration(after = HttpPayloadLoggingAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(HttpPayloadLogger.class)
public class ServletPayloadLoggingAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    public ServletPayloadLogging servletPayloadLogging(HttpPayloadLogger logger) { return new ServletPayloadLogging(logger); }
    @Bean @ConditionalOnMissingBean
    public PayloadBodyAdvice payloadBodyAdvice(ServletPayloadLogging logging) { return new PayloadBodyAdvice(logging); }
    @Bean
    public WebMvcConfigurer payloadLoggingWebMvcConfigurer(ServletPayloadLogging logging) {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new HandlerInterceptor() {
                    @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response, Object handler) {
                        if (!logging.active() || !(handler instanceof HandlerMethod method)
                                || !method.getBeanType().getPackageName().startsWith("com.fnbx.")) return true;
                        boolean jsonBody = false;
                        try {
                            jsonBody = PayloadBodyAdvice.json(org.springframework.http.MediaType.parseMediaType(
                                    request.getContentType() == null ? "application/octet-stream" : request.getContentType()))
                                    && java.util.Arrays.stream(method.getMethodParameters()).anyMatch(p -> p.hasParameterAnnotation(RequestBody.class));
                        } catch (IllegalArgumentException invalidContentType) {
                            // Let MVC handle the invalid header using its normal error contract.
                        }
                        if (!jsonBody) logging.request(request, null);
                        return true;
                    }
                });
            }
        };
    }
}
