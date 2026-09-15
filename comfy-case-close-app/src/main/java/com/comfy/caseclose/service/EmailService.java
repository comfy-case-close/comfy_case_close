package com.comfy.caseclose.service;

import com.comfy.caseclose.integration.EmailMessage;
import com.comfy.caseclose.integration.MailDeliveryException;
import com.comfy.caseclose.integration.MailTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/** Vakot's template composition and three-attempt delivery loop, adapted for cash closes. */
@Service
@Slf4j
public class EmailService {
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private final MailTransport mailTransport;
    private final ITemplateEngine templateEngine;
    private final String from;
    private final String frontendBaseUrl;
    private final String reviewCashClosePathTemplate;
    private final String submitterCashClosePathTemplate;

    public EmailService(MailTransport mailTransport, ITemplateEngine templateEngine,
                        @Value("${app.mail.from:}") String from,
                        @Value("${app.frontend.base-url:http://localhost:3000}") String frontendBaseUrl,
                        @Value("${app.frontend.cash-close-review-path-template:/approvals?cashCloseId={id}}")
                        String reviewCashClosePathTemplate,
                        @Value("${app.frontend.cash-close-submitter-path-template:/history?cashCloseId={id}}")
                        String submitterCashClosePathTemplate) {
        this.mailTransport = mailTransport;
        this.templateEngine = templateEngine;
        this.from = from;
        this.frontendBaseUrl = stripTrailingSlashes(frontendBaseUrl);
        this.reviewCashClosePathTemplate = normalizePathTemplate(
                reviewCashClosePathTemplate, "/approvals?cashCloseId={id}");
        this.submitterCashClosePathTemplate = normalizePathTemplate(
                submitterCashClosePathTemplate, "/history?cashCloseId={id}");
    }

    public void sendCashCloseSubmitted(String to, CashCloseSubmittedEvent event) {
        sendCashCloseSubmitted(to, event, reviewCashClosePathTemplate);
    }

    public void sendCashCloseSubmittedToSubmitter(String to, CashCloseSubmittedEvent event) {
        sendCashCloseSubmitted(to, event, submitterCashClosePathTemplate);
    }

    private void sendCashCloseSubmitted(String to, CashCloseSubmittedEvent event, String pathTemplate) {
        String viewSubmissionUrl = frontendBaseUrl
                + pathTemplate.replace("{id}", event.cashCloseId().toString());
        Context context = new Context();
        context.setVariable("close", event);
        context.setVariable("viewSubmissionUrl", viewSubmissionUrl);
        String text = """
                A cash close has been submitted.

                Reference: %s
                Branch: %s
                Shift: %s
                Business date: %s
                Submitted by: %s
                Status: %s

                Please sign in to Comfy Cash Close to review the submission.
                View submission: %s
                """.formatted(event.referenceCode(), event.branchCode(), event.shiftTypeCode(),
                event.businessDate(), event.submittedBy(), event.status(), viewSubmissionUrl);
        EmailMessage message = new EmailMessage(from, to,
                "[Comfy Cash Close] " + event.branchCode() + " - " + event.shiftTypeCode()
                        + " - " + event.businessDate(),
                text, templateEngine.process("email/cash-close-submitted", context));
        deliver(message, event.cashCloseId());
    }

    private static String stripTrailingSlashes(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePathTemplate(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.strip();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private void deliver(EmailMessage message, Long cashCloseId) {
        long startedAt = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                mailTransport.send(message);
                log.info("Cash close {} email sent to {} on attempt {} in {} ms",
                        cashCloseId, message.to(), attempt, (System.nanoTime() - startedAt) / 1_000_000);
                return;
            } catch (MailDeliveryException ex) {
                if (!ex.isRetryable() || attempt == MAX_DELIVERY_ATTEMPTS) {
                    log.warn("Cash close {} email failed after {} attempt(s); providerFault={}",
                            cashCloseId, attempt, ex.isProviderFault());
                    throw ex;
                }
                log.warn("Transient email failure for cash close {} on attempt {}/{}; retrying",
                        cashCloseId, attempt, MAX_DELIVERY_ATTEMPTS);
            }
        }
    }
}
