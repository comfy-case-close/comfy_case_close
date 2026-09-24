package com.fnbx.identity.service;

import com.fnbx.identity.utils.enums.OtpPurpose;
import com.fnbx.mail.EmailService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Request-side facade: provider readiness and asynchronous event publication. */
@Component
public class OtpMailer {
    private final EmailService email;
    private final MailHealth health;
    private final ApplicationEventPublisher events;

    public OtpMailer(EmailService email, MailHealth health, ApplicationEventPublisher events) {
        this.email = email;
        this.health = health;
        this.events = events;
    }

    public boolean available() { return !health.isDown() && email.available(); }

    public void mail(OtpPurpose purpose, String scope, String address, String otp) {
        events.publishEvent(new OtpEmailRequested(purpose, scope, address, otp));
    }
}
