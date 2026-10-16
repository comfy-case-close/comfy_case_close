package com.fnbx.shared.logging;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import com.fnbx.shared.tenant.TenantContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.support.*;

/** One implementation for all annotated API operations; no payloads, tokens or financial values. */
public class ServiceOperationLoggingInterceptor implements MethodInterceptor {
    private static final Set<String> RESOURCE_IDS = Set.of("branchId", "staffId", "employeeId", "positionId",
            "departmentId", "assignmentId", "periodId", "payrollLineId", "payslipId", "paymentId", "fileId",
            "scheduleId", "shiftId", "attendanceId", "withdrawalId", "cashCloseId", "movementId", "runId");

    static LogOperation annotation(Method method, Class<?> target) {
        return AnnotatedElementUtils.findMergedAnnotation(AopUtils.getMostSpecificMethod(method, target), LogOperation.class);
    }

    @Override public Object invoke(MethodInvocation invocation) throws Throwable {
        Class<?> target = AopUtils.getTargetClass(invocation.getThis());
        LogOperation operation = annotation(invocation.getMethod(), target);
        if (operation == null) return invocation.proceed();
        Logger log = LoggerFactory.getLogger(target);
        String name = target.getSimpleName() + "." + invocation.getMethod().getName();
        TenantContext tenant = TenantContext.currentOrNull();
        Object business = tenant == null ? null : tenant.businessId();
        Object actor = tenant == null ? null : tenant.userId();
        Map<String, UUID> resources = new LinkedHashMap<>();
        var parameters = AopUtils.getMostSpecificMethod(invocation.getMethod(), target).getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (RESOURCE_IDS.contains(parameters[i].getName()) && invocation.getArguments()[i] instanceof UUID id) {
                resources.put(parameters[i].getName(), id);
            }
        }
        long started = System.nanoTime();
        log.debug("Service operation started operation={} businessId={} actorId={} resourceIds={}", name, business, actor, resources);
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable failure) {
            // The HTTP handler owns WARN/ERROR and the sanitized diagnostic, exactly once.
            log.debug("Service operation failed operation={} exceptionType={} durationMs={}",
                    name, failure.getClass().getName(), (System.nanoTime() - started) / 1_000_000);
            throw failure;
        }
        long duration = (System.nanoTime() - started) / 1_000_000;
        if (operation.write()) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            Runnable success = () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (context == null) MDC.clear(); else MDC.setContextMap(context);
                    log.info("Service operation completed operation={} businessId={} actorId={} resourceIds={} durationMs={}",
                            name, business, actor, resources, duration);
                } finally {
                    if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
                }
            };
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { success.run(); }
                });
            } else success.run();
        } else {
            log.debug("Service operation completed operation={} businessId={} actorId={} resourceIds={} durationMs={}",
                    name, business, actor, resources, duration);
        }
        return result;
    }
}
