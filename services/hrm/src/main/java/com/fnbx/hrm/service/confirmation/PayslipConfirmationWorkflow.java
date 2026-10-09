package com.fnbx.hrm.service.confirmation;

import com.fnbx.hrm.config.PayslipConfirmationProperties;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.entity.PayslipConfirmation;
import com.fnbx.hrm.enums.ConfirmationStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.PayslipConfirmationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayslipConfirmationWorkflow {

    private final PayslipConfirmationRepository repository;
    private final PayslipConfirmationTokens tokens;
    private final PayslipConfirmationProperties properties;

    /** Rotates the token of the payslip's confirmation, so earlier links stop working, and returns the new one. */
    public String issue(Payslip payslip) {
        String token = tokens.generate();
        PayslipConfirmation confirmation = repository.findByPayslipId(payslip.getPayslipId())
                .orElseGet(() -> create(payslip));
        confirmation.setTokenHash(tokens.hash(token));
        confirmation.setTokenExpiresAt(Instant.now().plus(Duration.ofDays(properties.tokenTtlDays())));
        if (confirmation.getStatus() == ConfirmationStatus.DISPUTED) {
            confirmation.setStatus(ConfirmationStatus.PENDING);
        }
        repository.save(confirmation);
        return token;
    }

    public PayslipConfirmation requireUsable(PayslipConfirmation confirmation) {
        if (confirmation.getTokenExpiresAt().isBefore(Instant.now())) {
            throw PayrollExceptions.confirmationTokenInvalid();
        }
        return confirmation;
    }

    public void confirm(PayslipConfirmation confirmation, ClientInfo client) {
        if (confirmation.getStatus() == ConfirmationStatus.CONFIRMED) {
            return;
        }
        if (confirmation.getStatus() == ConfirmationStatus.DISPUTED) {
            throw PayrollExceptions.resourceConflict("This payslip was reported as incorrect and is being reviewed");
        }
        confirmation.setStatus(ConfirmationStatus.CONFIRMED);
        confirmation.setConfirmedAt(Instant.now());
        recordClient(confirmation, client);
        repository.save(confirmation);
    }

    public void dispute(PayslipConfirmation confirmation, String note, ClientInfo client) {
        if (confirmation.getStatus() == ConfirmationStatus.CONFIRMED) {
            throw PayrollExceptions.resourceConflict("This payslip was already confirmed");
        }
        confirmation.setStatus(ConfirmationStatus.DISPUTED);
        confirmation.setDisputeNote(note);
        recordClient(confirmation, client);
        repository.save(confirmation);
    }

    public void markReminded(PayslipConfirmation confirmation) {
        confirmation.setReminderCount((short) (confirmation.getReminderCount() + 1));
        confirmation.setLastRemindedAt(Instant.now());
        repository.save(confirmation);
    }

    private void recordClient(PayslipConfirmation confirmation, ClientInfo client) {
        confirmation.setRespondedIp(client.ip());
        confirmation.setRespondedUserAgent(client.userAgent());
    }

    private PayslipConfirmation create(Payslip payslip) {
        PayslipConfirmation confirmation = new PayslipConfirmation();
        confirmation.setPayslipConfirmationId(UUID.randomUUID());
        confirmation.setBusinessId(payslip.getBusinessId());
        confirmation.setPayslipId(payslip.getPayslipId());
        confirmation.setStatus(ConfirmationStatus.PENDING);
        confirmation.setCreatedAt(Instant.now());
        return confirmation;
    }
}
