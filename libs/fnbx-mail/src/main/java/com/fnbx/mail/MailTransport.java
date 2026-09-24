package com.fnbx.mail;

/** Delivers synchronously, throwing MailDeliveryException when the handoff fails. */
public interface MailTransport {
    void send(EmailMessage message);

    boolean configured();
}
