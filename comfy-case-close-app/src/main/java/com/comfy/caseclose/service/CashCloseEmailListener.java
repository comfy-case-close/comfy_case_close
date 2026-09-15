package com.comfy.caseclose.service;

import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.utils.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Vakot's after-commit email pattern: delivery failures never roll back a submitted close. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true", matchIfMissing = true)
public class CashCloseEmailListener {
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCashCloseSubmitted(CashCloseSubmittedEvent event) {
        notifyReviewRecipients(event);
        notifySubmitter(event);
    }

    private void notifyReviewRecipients(CashCloseSubmittedEvent event) {
        try {
            var recipients = reviewRecipients(event);
            if (recipients.isEmpty()) {
                log.info("No manager/admin email recipients for cash close {} in branch {}",
                        event.cashCloseId(), event.branchId());
            }
            for (String recipient : recipients) {
                try {
                    emailService.sendCashCloseSubmitted(recipient, event);
                } catch (RuntimeException ex) {
                    // One invalid address must not prevent delivery to the other reviewers.
                    log.warn("Cash close {} review notification not delivered; submission is unaffected",
                            event.cashCloseId());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not look up review notification recipients for cash close {}", event.cashCloseId());
        }
    }

    private Set<String> reviewRecipients(CashCloseSubmittedEvent event) {
        Set<String> recipients = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        recipients.addAll(userRepository.findActiveEmailsByBranchIdAndRoles(
                event.branchId(), List.of(UserRole.MANAGER, UserRole.ADMIN)));
        if (hasText(event.submittedByEmail())) {
            recipients.remove(event.submittedByEmail().strip());
        }
        return recipients;
    }

    private void notifySubmitter(CashCloseSubmittedEvent event) {
        if (!hasText(event.submittedByEmail())) {
            log.info("No submitter email recipient for cash close {}", event.cashCloseId());
            return;
        }
        try {
            emailService.sendCashCloseSubmittedToSubmitter(event.submittedByEmail().strip(), event);
        } catch (RuntimeException ex) {
            log.warn("Cash close {} submitter notification not delivered; submission is unaffected",
                    event.cashCloseId());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
