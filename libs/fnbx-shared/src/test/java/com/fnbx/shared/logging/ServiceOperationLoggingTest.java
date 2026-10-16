package com.fnbx.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class ServiceOperationLoggingTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OperationLoggingAutoConfiguration.class))
            .withUserConfiguration(Probes.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(Operations.class);
    private Level previous;
    @BeforeEach void setup() { previous = logger.getLevel(); logger.setLevel(Level.DEBUG); }
    @AfterEach void clear() { logger.setLevel(previous); MDC.clear(); }

    @Test void realProxyLogsReadsAtDebugWithoutPayloadOrResult(CapturedOutput output) {
        runner.run(context -> {
            var service = context.getBean(Operations.class);
            assertThat(AopUtils.isCglibProxy(service)).isTrue();
            Object secret = new Object() { @Override public String toString() { throw new AssertionError("must never serialize"); } };
            assertThat(service.read(secret)).isSameAs(secret);
            assertThat(output).contains("Service operation started operation=Operations.read", "Service operation completed operation=Operations.read")
                    .doesNotContain("secret-token", "hunter2");
        });
    }

    @Test void infoDoesNotShowReadOrStartLogsButShowsCommittedWrites(CapturedOutput output) {
        logger.setLevel(Level.INFO);
        runner.run(context -> {
            var service = context.getBean(Operations.class);
            service.read("secret-token");
            service.write();
            assertThat(output).contains("Service operation completed operation=Operations.write")
                    .doesNotContain("Operations.read", "Service operation started", "secret-token");
        });
    }

    @Test void joinedTransactionWaitsForCommitAndRestoresMdc(CapturedOutput output) {
        runner.run(context -> {
            MDC.put("requestId", "correlation-1");
            new TransactionTemplate(context.getBean(TestTransactions.class)).executeWithoutResult(status -> {
                context.getBean(Operations.class).write();
                assertThat(output).doesNotContain("Service operation completed operation=Operations.write");
                MDC.put("requestId", "caller-context");
            });
            assertThat(output).contains("Service operation completed operation=Operations.write");
            assertThat(MDC.get("requestId")).isEqualTo("caller-context");
        });
    }

    @Test void rollbackDoesNotLogWriteSuccess(CapturedOutput output) {
        runner.run(context -> {
            new TransactionTemplate(context.getBean(TestTransactions.class)).executeWithoutResult(status -> {
                context.getBean(Operations.class).write();
                status.setRollbackOnly();
            });
            assertThat(output).doesNotContain("Service operation completed operation=Operations.write");
        });
    }

    @Test void failedCommitDoesNotLogSuccess(CapturedOutput output) {
        runner.run(context -> {
            context.getBean(TestTransactions.class).failCommit = true;
            assertThatThrownBy(() -> context.getBean(Operations.class).write()).isInstanceOf(TransactionSystemException.class);
            assertThat(output).contains("Service operation failed operation=Operations.write")
                    .doesNotContain("Service operation completed operation=Operations.write", "private database detail");
        });
    }

    @Test void failuresPropagateUnchangedWithoutDuplicatingErrorLogs(CapturedOutput output) {
        runner.run(context -> {
            var failure = new IllegalStateException("hunter2");
            assertThatThrownBy(() -> context.getBean(Operations.class).fail(failure)).isSameAs(failure);
            assertThat(output).contains("Service operation failed operation=Operations.fail")
                    .doesNotContain("hunter2", "Service operation completed operation=Operations.fail", "ERROR", "WARN");
        });
    }

    @Test void logsOnlyAllowlistedUuidResourcesNeverUuidTokens(CapturedOutput output) {
        runner.run(context -> {
            var branch = java.util.UUID.randomUUID();
            var secret = java.util.UUID.randomUUID();
            context.getBean(Operations.class).resource(branch, secret, "secret-password");
            assertThat(output).contains("branchId=" + branch).doesNotContain(secret.toString(), "secret-password");
        });
    }

    @Test void nonTransactionalWritesLogOnlyAfterSuccessfulReturn(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context.getBean(Operations.class).externalAction()).isEqualTo("private-result");
            assertThat(output).contains("Service operation completed operation=Operations.externalAction")
                    .doesNotContain("private-result");
        });
    }

    @Test void unannotatedMethodsAreNotLoggedAndConfigurationCanBeDisabled(CapturedOutput output) {
        runner.run(context -> context.getBean(Operations.class).notLogged());
        runner.withPropertyValues("fnb.logging.operation.enabled=false").run(context -> {
            context.getBean(Operations.class).read("private-value");
            assertThat(context).doesNotHaveBean("operationLoggingAdvisor");
        });
        assertThat(output).doesNotContain("operation=");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Probes {
        @Bean Operations operations() { return new Operations(); }
        @Bean TestTransactions transactions() { return new TestTransactions(); }
    }

    public static class Operations {
        @LogOperation public Object read(Object input) { return input; }
        @LogOperation(write = true) @Transactional public void write() {}
        @LogOperation(write = true) public void fail(RuntimeException failure) { throw failure; }
        @LogOperation public void resource(java.util.UUID branchId, java.util.UUID token, String password) {}
        @LogOperation(write = true) public String externalAction() { return "private-result"; }
        public void notLogged() {}
    }

    static class TestTransactions extends AbstractPlatformTransactionManager {
        boolean failCommit;
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object tx) { return active.get(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { active.set(true); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("private database detail");
        }
        @Override protected void doRollback(DefaultTransactionStatus status) {}
        @Override protected void doCleanupAfterCompletion(Object tx) { active.remove(); }
    }
}
