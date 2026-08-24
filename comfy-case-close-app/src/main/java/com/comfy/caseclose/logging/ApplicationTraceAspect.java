package com.comfy.caseclose.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApplicationTraceAspect {

    @Around("within(com.comfy.caseclose.controller..*)")
    public Object traceController(ProceedingJoinPoint joinPoint) throws Throwable {
        return trace(joinPoint, "API");
    }

    @Around("within(com.comfy.caseclose.service.impl..*)")
    public Object traceServiceImpl(ProceedingJoinPoint joinPoint) throws Throwable {
        return trace(joinPoint, "SERVICE");
    }

    private Object trace(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        long startedAt = System.nanoTime();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Logger logger = LoggerFactory.getLogger(targetClass);
        String method = targetClass.getSimpleName() + "." + signature.getName();

        logger.info("{} -> {} args={}", layer, method,
                LoggingValueSanitizer.summarizeArguments(signature.getParameterNames(), joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            logger.info("{} <- {} result={} durationMs={}",
                    layer, method, LoggingValueSanitizer.summarizeResult(result), durationMs);
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            logger.warn("{} !! {} exception={} message={} durationMs={}",
                    layer, method, ex.getClass().getSimpleName(),
                    LoggingValueSanitizer.sanitizeMessage(ex.getMessage()), durationMs);
            throw ex;
        }
    }
}
