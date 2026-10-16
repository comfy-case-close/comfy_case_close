package com.fnbx.shared.logging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import static org.assertj.core.api.Assertions.*;

class LoggingAutoConfigurationTest {
    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RequestLoggingAutoConfiguration.class))
            .withPropertyValues("spring.application.name=probe-service");

    @Test void autoConfigurationsAreDiscoverableFromTheSharedJar() {
        assertThat(ImportCandidates.load(org.springframework.boot.autoconfigure.AutoConfiguration.class,
                getClass().getClassLoader()).getCandidates())
                .contains(RequestLoggingAutoConfiguration.class.getName(), OperationLoggingAutoConfiguration.class.getName());
    }

    @Test void createsExactlyOneFilterBeforeSecurity() {
        web.run(context -> {
            assertThat(context).hasSingleBean(RequestLoggingFilter.class);
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter()).isSameAs(context.getBean(RequestLoggingFilter.class));
        });
    }

    @Test void serviceCanReplaceTheSharedFilterWithoutBeanOverriding() {
        web.withUserConfiguration(CustomFilter.class).run(context -> {
            assertThat(context).hasSingleBean(RequestLoggingFilter.class);
            assertThat(context.getBean(RequestLoggingFilter.class)).isInstanceOf(CustomRequestFilter.class);
        });
    }

    @Test void canDisableRequestLogging() {
        web.withPropertyValues("fnb.logging.request.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(RequestLoggingFilter.class));
    }

    @Test void servletFilterIsNotLoadedInANonServletApplication() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RequestLoggingAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RequestLoggingFilter.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFilter {
        @Bean RequestLoggingFilter customRequestLoggingFilter() { return new CustomRequestFilter(); }
    }
    static class CustomRequestFilter extends RequestLoggingFilter {
        CustomRequestFilter() { super("custom-service"); }
    }
}
