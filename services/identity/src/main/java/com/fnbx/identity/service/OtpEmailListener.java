package com.fnbx.identity.service;

import java.util.concurrent.Executor;
import com.fnbx.mail.EmailService;
import com.fnbx.mail.MailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OtpEmailListener {
    private static final Logger log = LoggerFactory.getLogger(OtpEmailListener.class);
    private final EmailService email;
    private final VerificationStore store;
    private final MailHealth health;
    private final Executor executor;

    public OtpEmailListener(EmailService email, VerificationStore store, MailHealth health,
            @Qualifier("emailTaskExecutor") Executor executor) {
        this.email = email;
        this.store = store;
        this.health = health;
        this.executor = executor;
    }

    @EventListener
    public void onOtpRequested(OtpEmailRequested event) {
        // OTP storage is in-memory, not transactional. A transaction listener would drop these events.
        // Rejection propagates to the caller, which discards the OTP and returns deliveryUnavailable.
        executor.execute(() -> deliver(event));
    }

    private void deliver(OtpEmailRequested event) {
        try {
            String subject = switch (event.purpose()) {
                case SIGNUP -> "Verify your FNB account";
                case BUSINESS_REGISTRATION -> "Verify your FNB business registration email";
                case PASSWORD_RESET -> "Reset your FNB password";
            };
            email.sendText(event.email(), subject, "Your verification code is " + event.otp()
                    + ". It expires in 10 minutes. If you did not request it, ignore this email.");
            health.recordSuccess();
        } catch (RuntimeException failed) {
            // A delayed failure must not invalidate a newer OTP requested for the same scope.
            store.discardOtp(event.purpose(), event.scope(), event.otp());
            if (failed instanceof MailDeliveryException delivery && delivery.isProviderFault()) {
                health.recordProviderFault("Mail provider configuration or authentication failure");
            }
            log.warn("{} verification email was not delivered", event.purpose());
        }
    }
}
