package com.comfy.caseclose.integration;

/** Delivers synchronously, throwing MailDeliveryException when the handoff fails. */
public interface MailTransport {
    void send(EmailMessage message);
}
