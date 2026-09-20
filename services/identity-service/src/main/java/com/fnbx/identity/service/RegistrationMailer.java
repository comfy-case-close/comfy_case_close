package com.fnbx.identity.service;

import java.time.Clock;
import java.util.UUID;
import com.fnbx.identity.entity.BusinessRegistration;
import com.fnbx.identity.repository.BusinessRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The decision letter a business registration ends with.
 *
 * <h2>Why this is not {@link OtpMailer}</h2>
 * An OTP is a secret with a lifetime: if delivery fails, the code is discarded and
 * the user asks for another, so failure is recoverable by retrying the whole
 * action. A decision letter is the opposite - the decision has already been made
 * and committed, it cannot be un-made, and the applicant has no way to ask again.
 * So this sends after the transaction commits, never inside it, and records
 * {@code notified_at} on success. A decided registration still showing null there
 * is a person who was never told, which a reviewer can see and act on.
 *
 * <h2>Why identity sends it at all</h2>
 * Notification belongs in notify-service, and this should move there
 * (docs/security/onboarding.md section 8.3). It lives here for now because
 * identity is the only service that may read {@code business_registration} - the
 * migration revokes it from everyone else - so moving the letter means moving the
 * read across a service boundary, which is a change worth making deliberately
 * rather than as a side effect of shipping registration.
 */
@Component
public class RegistrationMailer {

    private static final Logger log = LoggerFactory.getLogger(RegistrationMailer.class);

    private final ObjectProvider<JavaMailSender> senders;
    private final BusinessRegistrationRepository registrations;
    private final TenantTransactions transactions;
    private final MailHealth health;
    private final Clock clock;
    private final String from;

    public RegistrationMailer(ObjectProvider<JavaMailSender> senders, BusinessRegistrationRepository registrations,
            TenantTransactions transactions, MailHealth health, Clock clock,
            @Value("${fnb.mail.from:}") String from) {
        this.senders = senders; this.registrations = registrations; this.transactions = transactions;
        this.health = health; this.clock = clock; this.from = from;
    }

    /** Sends the generated credential after approval commits. Only the password hash is persisted. */
    @Async("emailTaskExecutor")
    public void approved(BusinessRegistration registration, String password) {
        send(registration.registrationId(), registration.ownerEmail(),
                "Your F&B Nexus registration for " + registration.businessName() + " was approved",
                """
                Hello %s,

                %s has been approved on F&B Nexus.

                  Business code : %s
                  Owner email   : %s
                  Password      : %s

                Your verified owner account is ready. Sign in using the business
                code, email and password above, then change your password immediately
                in account settings. Keep this password private.

                If you lose this email, use Forgot Password with your business code
                and owner email to choose a new password.

                If you did not apply for this, please reply and tell us.
                """.formatted(registration.ownerFirstName(), registration.businessName(),
                        registration.businessCode(), registration.ownerEmail(), password));
    }

    /**
     * Tells the applicant why they were turned down.
     *
     * <p>{@code decisionNote} is written by a reviewer and reproduced verbatim - it is
     * the only explanation the applicant gets, and the reason the database refuses a
     * rejection without one.
     */
    @Async("emailTaskExecutor")
    public void rejected(BusinessRegistration registration, String reason) {
        send(registration.registrationId(), registration.ownerEmail(),
                "Your F&B Nexus registration for " + registration.businessName() + " was not approved",
                """
                Hello %s,

                We were not able to approve the registration for %s.

                  Reason: %s

                You are welcome to apply again once that has been resolved.

                If you did not apply for this, no account was created and you can
                ignore this message.
                """.formatted(registration.ownerFirstName(), registration.businessName(), reason));
    }

    private void send(UUID registrationId, String email, String subject, String body) {
        try {
            JavaMailSender sender = senders.getIfAvailable();
            if (sender == null || from.isBlank()) throw new IllegalStateException("Mail is not configured");
            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            health.recordSuccess();
            // No tenant context: business_registration carries no business_id and takes no
            // RLS policy, so this runs outside any tenant - see the table's migration.
            transactions.outsideTenant(() -> {
                registrations.markNotified(registrationId, clock.instant());
                return null;
            });
        } catch (RuntimeException undelivered) {
            if (undelivered instanceof org.springframework.mail.MailAuthenticationException
                    || undelivered.getCause() instanceof java.net.ConnectException) {
                health.recordProviderFault("SMTP authentication or connection failure");
            }
            // No recipient, no subject, no provider exception text: this line goes to a log
            // that is not tenant-scoped. notified_at staying null is the durable signal.
            log.warn("Business registration decision letter was not delivered");
        }
    }
}
