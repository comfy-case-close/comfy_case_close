package com.comfy.caseclose.service;

import com.comfy.caseclose.integration.EmailMessage;
import com.comfy.caseclose.integration.MailDeliveryException;
import com.comfy.caseclose.integration.MailTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    private final MailTransport transport = mock(MailTransport.class);
    private EmailService service;
    private final CashCloseSubmittedEvent event = new CashCloseSubmittedEvent(
            101L, 1L, "CC-101", "TX", "MORNING_CLOSE", LocalDate.of(2026, 9, 14),
            "Tâm <script>alert(1)</script>", "submitter@example.com", "PENDING_REVIEW");

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        service = new EmailService(transport, engine, "cash-close@example.com", "https://app.example.com/",
                "/approvals?cashCloseId={id}", "/history?cashCloseId={id}");
    }

    @Test
    void rendersSubmissionInBothBodiesAndEscapesHtml() {
        service.sendCashCloseSubmitted("lead@example.com", event);
        var capture = ArgumentCaptor.forClass(EmailMessage.class);
        verify(transport).send(capture.capture());
        EmailMessage message = capture.getValue();
        assertThat(message.from()).isEqualTo("cash-close@example.com");
        assertThat(message.to()).isEqualTo("lead@example.com");
        assertThat(message.subject()).contains("TX", "MORNING_CLOSE", "2026-09-14");
        assertThat(message.text()).contains("CC-101", "TX", "MORNING_CLOSE", "PENDING_REVIEW", event.submittedBy(),
                "https://app.example.com/approvals?cashCloseId=101");
        assertThat(message.html()).contains("CC-101", "TX", "MORNING_CLOSE", "PENDING_REVIEW", "Tâm &lt;script&gt;")
                .contains("href=\"https://app.example.com/approvals?cashCloseId=101\"", "View submission")
                .doesNotContain("<script>");
    }

    @Test
    void rendersSubmitterLinkToHistory() {
        service.sendCashCloseSubmittedToSubmitter("submitter@example.com", event);

        var capture = ArgumentCaptor.forClass(EmailMessage.class);
        verify(transport).send(capture.capture());
        assertThat(capture.getValue().html())
                .contains("href=\"https://app.example.com/history?cashCloseId=101\"");
        assertThat(capture.getValue().text()).contains("https://app.example.com/history?cashCloseId=101");
    }

    @Test
    void retriesTransientFailureThenSucceeds() {
        doThrow(MailDeliveryException.retryable("timeout")).doNothing().when(transport).send(any());
        service.sendCashCloseSubmitted("lead@example.com", event);
        verify(transport, times(2)).send(any());
    }

    @Test
    void stopsAfterThreeTransientFailures() {
        doThrow(MailDeliveryException.retryable("timeout")).when(transport).send(any());
        assertThatThrownBy(() -> service.sendCashCloseSubmitted("lead@example.com", event))
                .isInstanceOf(MailDeliveryException.class);
        verify(transport, times(3)).send(any());
    }

    @Test
    void doesNotRetryPermanentFailure() {
        doThrow(MailDeliveryException.providerMisconfigured("bad credentials")).when(transport).send(any());
        assertThatThrownBy(() -> service.sendCashCloseSubmitted("lead@example.com", event))
                .isInstanceOf(MailDeliveryException.class);
        verify(transport).send(any());
    }

    @Test
    void supportsConfiguredFrontendPathTemplates() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        EmailService customService = new EmailService(transport, engine, "cash-close@example.com",
                "https://app.example.com/", "/review?id={id}", "/close?id={id}");

        customService.sendCashCloseSubmitted("lead@example.com", event);
        customService.sendCashCloseSubmittedToSubmitter("submitter@example.com", event);

        var capture = ArgumentCaptor.forClass(EmailMessage.class);
        verify(transport, times(2)).send(capture.capture());
        assertThat(capture.getAllValues().get(0).html()).contains("href=\"https://app.example.com/review?id=101\"");
        assertThat(capture.getAllValues().get(1).html()).contains("href=\"https://app.example.com/close?id=101\"");
    }
}
