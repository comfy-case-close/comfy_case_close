package com.comfy.caseclose.service;

import com.comfy.caseclose.config.AsyncConfiguration;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.utils.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashCloseEmailListenerTest {
    private static final List<UserRole> REVIEW_ROLES = List.of(UserRole.MANAGER, UserRole.ADMIN);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailService emails = mock(EmailService.class);
    private final CashCloseSubmittedEvent event = new CashCloseSubmittedEvent(
            101L, 1L, "CC-101", "TX", "MORNING_CLOSE", LocalDate.of(2026, 9, 14),
            "User A", "submitter@example.com", "SUBMITTED");

    @Test
    void notifiesReviewersAndSubmitterEvenWhenFirstReviewRecipientFails() {
        when(users.findActiveEmailsByBranchIdAndRoles(1L, REVIEW_ROLES))
                .thenReturn(List.of("manager@example.com", "admin@example.com", "submitter@example.com"));
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emails).sendCashCloseSubmitted("manager@example.com", event);

        assertThatCode(() -> new CashCloseEmailListener(users, emails).onCashCloseSubmitted(event)).doesNotThrowAnyException();

        verify(emails).sendCashCloseSubmitted("manager@example.com", event);
        verify(emails).sendCashCloseSubmitted("admin@example.com", event);
        verify(emails, never()).sendCashCloseSubmitted("submitter@example.com", event);
        verify(emails).sendCashCloseSubmittedToSubmitter("submitter@example.com", event);
        verify(users).findActiveEmailsByBranchIdAndRoles(1L, REVIEW_ROLES);
        verifyNoMoreInteractions(users, emails);
    }

    @Test
    void noReviewRecipientsStillNotifiesSubmitter() {
        when(users.findActiveEmailsByBranchIdAndRoles(1L, REVIEW_ROLES)).thenReturn(List.of());
        new CashCloseEmailListener(users, emails).onCashCloseSubmitted(event);
        verify(emails).sendCashCloseSubmittedToSubmitter("submitter@example.com", event);
        verifyNoMoreInteractions(emails);
    }

    @Test
    void recipientLookupFailureDoesNotEscape() {
        when(users.findActiveEmailsByBranchIdAndRoles(1L, REVIEW_ROLES)).thenThrow(new IllegalStateException());
        assertThatCode(() -> new CashCloseEmailListener(users, emails).onCashCloseSubmitted(event)).doesNotThrowAnyException();
        verify(emails).sendCashCloseSubmittedToSubmitter("submitter@example.com", event);
        verifyNoMoreInteractions(emails);
    }

    @Test
    void springWiringDeliversOnlyAfterCommitOnEmailExecutor() {
        try (var context = context()) {
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(TestTransactionManager.class));
            AtomicReference<String> threadName = new AtomicReference<>();
            when(users.findActiveEmailsByBranchIdAndRoles(1L, REVIEW_ROLES))
                    .thenReturn(List.of("manager@example.com"));
            doAnswer(call -> {
                threadName.set(Thread.currentThread().getName());
                return null;
            }).when(emails).sendCashCloseSubmitted("manager@example.com", event);

            // No transaction and rolled-back transactions must never submit email tasks.
            context.publishEvent(event);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                status.setRollbackOnly();
            });
            verifyNoInteractions(users, emails);

            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(users, emails);
            });
            verify(emails, timeout(3000)).sendCashCloseSubmitted("manager@example.com", event);
            verify(emails, timeout(3000)).sendCashCloseSubmittedToSubmitter("submitter@example.com", event);
            assertThat(threadName.get()).startsWith("email-");
        }
    }

    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(UserRepository.class, () -> users);
        context.registerBean(EmailService.class, () -> emails);
        context.register(TestConfiguration.class, AsyncConfiguration.class, CashCloseEmailListener.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean TestTransactionManager transactionManager() { return new TestTransactionManager(); }
    }

    /** Exercises Spring's actual commit/rollback event machinery without touching a database. */
    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
