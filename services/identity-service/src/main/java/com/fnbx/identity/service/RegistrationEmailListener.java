package com.fnbx.identity.service;

import java.time.Clock;
import java.util.concurrent.Executor;
import com.fnbx.identity.repository.BusinessRegistrationRepository;
import com.fnbx.mail.EmailService;
import com.fnbx.mail.MailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RegistrationEmailListener {
    private static final Logger log = LoggerFactory.getLogger(RegistrationEmailListener.class);
    private final EmailService email;
    private final BusinessRegistrationRepository registrations;
    private final TenantTransactions transactions;
    private final MailHealth health;
    private final Clock clock;
    private final Executor executor;

    public RegistrationEmailListener(EmailService email, BusinessRegistrationRepository registrations,
            TenantTransactions transactions, MailHealth health, Clock clock,
            @Qualifier("emailTaskExecutor") Executor executor) {
        this.email = email;
        this.registrations = registrations;
        this.transactions = transactions;
        this.health = health;
        this.clock = clock;
        this.executor = executor;
    }

    @EventListener
    public void onRegistrationEmailRequested(RegistrationEmailRequested event) {
        // Registration's service publishes after its explicit TransactionTemplate has committed.
        try {
            executor.execute(() -> deliver(event));
        } catch (RuntimeException rejected) {
            log.warn("Could not queue registration notification; decision is unaffected");
        }
    }

    private void deliver(RegistrationEmailRequested event) {
        try {
            email.sendText(event.email(), event.subject(), event.body());
            health.recordSuccess();
            transactions.outsideTenant(() -> {
                registrations.markNotified(event.registrationId(), clock.instant());
                return null;
            });
        } catch (RuntimeException failed) {
            if (failed instanceof MailDeliveryException delivery && delivery.isProviderFault()) {
                health.recordProviderFault("Mail provider configuration or authentication failure");
            }
            // notified_at remains null, preserving the existing operational signal.
            log.warn("Business registration decision letter was not delivered");
        }
    }
}
