package com.fnbx.identity.service;

import com.fnbx.identity.utils.enums.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Same asynchronous OTP delivery and failed-delivery invalidation as Vakot. */
@Component
public class OtpMailer {
    private static final Logger log = LoggerFactory.getLogger(OtpMailer.class);
    private final ObjectProvider<JavaMailSender> senders;
    private final VerificationStore store;
    private final String from;
    private final String host;
    private final MailHealth health;
    public OtpMailer(ObjectProvider<JavaMailSender> senders, VerificationStore store,
                     @Value("${fnb.mail.from:}") String from, @Value("${spring.mail.host:}") String host,
                     MailHealth health) {
        this.senders = senders; this.store = store; this.from = from; this.host = host; this.health = health;
    }

    public boolean available() { return !health.isDown() && !host.isBlank() && !from.isBlank() && senders.getIfAvailable() != null; }

    @Async("emailTaskExecutor")
    public void mail(OtpPurpose purpose, String scope, String email, String otp) {
        try {
            JavaMailSender sender = senders.getIfAvailable();
            if (sender == null || from.isBlank()) throw new IllegalStateException("Mail is not configured");
            var message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(email);
            message.setSubject(switch (purpose) {
                case SIGNUP -> "Verify your FNB account";
                case BUSINESS_REGISTRATION -> "Verify your FNB business registration email";
                case PASSWORD_RESET -> "Reset your FNB password";
            });
            message.setText("Your verification code is " + otp + ". It expires in 10 minutes. If you did not request it, ignore this email.");
            sender.send(message);
            health.recordSuccess();
        } catch (RuntimeException ex) {
            store.discardOtp(purpose, scope);
            if (ex instanceof org.springframework.mail.MailAuthenticationException
                    || ex.getCause() instanceof java.net.ConnectException) {
                health.recordProviderFault("SMTP authentication or connection failure");
            }
            log.warn("{} verification email was not delivered", purpose); // No recipient, OTP or provider exception.
        }
    }
}
