package com.fnbx.cashclose.service;

import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import com.fnbx.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** After-commit delivery from dev, with UUID snapshots and branch-scoped position recipients. */
@Component
@ConditionalOnProperty(name = "fnb.mail.enabled", havingValue = "true", matchIfMissing = true)
public class CashCloseEmailListener {
    private static final Logger log = LoggerFactory.getLogger(CashCloseEmailListener.class);
    private final EmailService email;
    private final Executor executor;
    private final String frontendBaseUrl;
    private final String reviewPath;
    private final String submitterPath;

    public CashCloseEmailListener(EmailService email, @Qualifier("emailTaskExecutor") Executor executor,
            @Value("${fnb.frontend.base-url:http://localhost:3000}") String frontendBaseUrl,
            @Value("${fnb.frontend.cash-close-review-path-template:/approvals?cashCloseId={id}}") String reviewPath,
            @Value("${fnb.frontend.cash-close-submitter-path-template:/history?cashCloseId={id}}") String submitterPath) {
        this.email = email;
        this.executor = executor;
        this.frontendBaseUrl = frontendBaseUrl.strip().replaceAll("/+$", "");
        this.reviewPath = path(reviewPath, "/approvals?cashCloseId={id}");
        this.submitterPath = path(submitterPath, "/history?cashCloseId={id}");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCashCloseSubmitted(CashCloseSubmittedEvent event) {
        try {
            // Catch queue rejection here too: the submission has already committed.
            executor.execute(() -> deliver(event));
        } catch (RuntimeException rejected) {
            log.warn("Could not queue cash close {} notification; submission is unaffected", event.cashCloseId());
        }
    }

    private void deliver(CashCloseSubmittedEvent event) {
        var managers = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        event.managerEmails().stream().filter(CashCloseEmailListener::hasText).map(String::strip).forEach(managers::add);
        if (hasText(event.submittedByEmail())) managers.remove(event.submittedByEmail().strip());
        for (String manager : managers) send(manager, event, reviewPath);
        if (hasText(event.submittedByEmail())) send(event.submittedByEmail().strip(), event, submitterPath);
    }

    private void send(String to, CashCloseSubmittedEvent event, String path) {
        try {
            String url = frontendBaseUrl + path.replace("{id}", event.cashCloseId().toString());
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
                    event.businessDate(), event.submittedBy(), event.status(), url);
            email.sendTemplate(to, "[Comfy Cash Close] " + event.branchCode() + " - " + event.shiftTypeCode()
                    + " - " + event.businessDate(), text, "email/cash-close-submitted",
                    Map.of("close", event, "viewSubmissionUrl", url));
        } catch (RuntimeException failed) {
            log.warn("Cash close {} notification was not delivered; submission is unaffected", event.cashCloseId());
        }
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String path(String value, String fallback) {
        if (!hasText(value)) return fallback;
        return value.strip().startsWith("/") ? value.strip() : "/" + value.strip();
    }
}
