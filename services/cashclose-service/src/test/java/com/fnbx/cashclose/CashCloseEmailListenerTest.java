package com.fnbx.cashclose;

import com.fnbx.cashclose.service.*;
import com.fnbx.mail.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashCloseEmailListenerTest {
    EmailService email = mock(EmailService.class);
    CashCloseEmailListener listener = new CashCloseEmailListener(email, Runnable::run, "https://comfy.test/",
            "/approvals?cashCloseId={id}", "/history?cashCloseId={id}");

    @Test void deduplicatesManagersAndSubmitterAndContinuesAfterOneFailure() {
        var event = event(List.of(" manager@example.test ", "MANAGER@example.test", "submitter@example.test", "other@example.test"));
        doThrow(MailDeliveryException.rejectedMessage("invalid")).when(email).sendTemplate(eq("manager@example.test"), any(), any(), any(), any());
        listener.onCashCloseSubmitted(event);
        verify(email).sendTemplate(eq("manager@example.test"), any(), contains("/approvals?cashCloseId=" + event.cashCloseId()), any(), any());
        verify(email).sendTemplate(eq("other@example.test"), any(), any(), any(), any());
        verify(email).sendTemplate(eq("submitter@example.test"), any(), contains("/history?cashCloseId=" + event.cashCloseId()), any(), any());
        verify(email, times(3)).sendTemplate(any(), any(), any(), any(), any());
    }

    @Test void queueRejectionDoesNotTurnCommittedSubmissionIntoFailure() {
        var saturated = new CashCloseEmailListener(email, task -> { throw new RejectedExecutionException(); }, "", "", "");
        assertThatCode(() -> saturated.onCashCloseSubmitted(event(List.of()))).doesNotThrowAnyException();
        verifyNoInteractions(email);
    }

    @Test void realTemplateRendersUuidLinkAndEscapesUserContent() {
        var transport = mock(MailTransport.class);
        var service = new EmailService(transport, new MailConfiguration().emailTemplateEngine(), "sender@example.test", true);
        var rendering = new CashCloseEmailListener(service, Runnable::run, "https://comfy.test", "", "");
        var event = event(List.of());
        rendering.onCashCloseSubmitted(event);
        var message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(transport).send(message.capture());
        assertThat(message.getValue().html()).contains("https://comfy.test/history?cashCloseId=" + event.cashCloseId(), "&lt;Submitter&gt;")
                .doesNotContain("<Submitter>");
    }

    @Test void transactionListenerRunsOnlyAfterCommitAndNeverAfterRollback() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(CashCloseEmailListener.class, () -> listener);
            context.refresh();
            var transaction = new TransactionTemplate(new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object transaction, TransactionDefinition definition) {}
                protected void doCommit(DefaultTransactionStatus status) {}
                protected void doRollback(DefaultTransactionStatus status) {}
            });
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event(List.of()));
                verifyNoInteractions(email);
                status.setRollbackOnly();
            });
            verifyNoInteractions(email);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event(List.of()));
                verifyNoInteractions(email);
            });
            verify(email).sendTemplate(eq("submitter@example.test"), any(), any(), any(), any());
        }
    }

    static CashCloseSubmittedEvent event(List<String> managers) {
        return new CashCloseSubmittedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CC-001", "TX", "AM",
                LocalDate.of(2026, 9, 23), "<Submitter>", "submitter@example.test", "SUBMITTED", managers);
    }
}
