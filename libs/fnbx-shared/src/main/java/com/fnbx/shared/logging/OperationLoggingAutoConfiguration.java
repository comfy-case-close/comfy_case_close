package com.fnbx.shared.logging;

import java.lang.reflect.Modifier;
import org.springframework.aop.Advisor;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.type.AnnotationMetadata;

@AutoConfiguration
@ConditionalOnProperty(prefix = "fnb.logging.operation", name = "enabled", matchIfMissing = true)
@Import(OperationLoggingAutoConfiguration.ProxyRegistrar.class)
public class OperationLoggingAutoConfiguration {
    @Bean
    @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "operationLoggingAdvisor")
    public static Advisor operationLoggingAdvisor() {
        var pointcut = new StaticMethodMatcherPointcut() {
            @Override public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                return Modifier.isPublic(method.getModifiers())
                        && ServiceOperationLoggingInterceptor.annotation(method, targetClass) != null;
            }
        };
        var advisor = new DefaultPointcutAdvisor(pointcut, new ServiceOperationLoggingInterceptor());
        // Wrap transaction advice: a failed commit must never look like a successful write.
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return advisor;
    }

    static class ProxyRegistrar implements ImportBeanDefinitionRegistrar {
        @Override public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
            AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
        }
    }
}
