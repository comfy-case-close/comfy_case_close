package com.fnbx.mail;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.web.util.HtmlUtils;

/** Template composition and dev's three-attempt delivery loop, shared by both services. */
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private final MailTransport transport;
    private final ITemplateEngine templates;
    private final String from;
    private final boolean enabled;

    public EmailService(MailTransport transport, ITemplateEngine templates,
            @Value("${fnb.mail.from:}") String from,
            @Value("${fnb.mail.enabled:true}") boolean enabled) {
        this.transport = transport;
        this.templates = templates;
        this.from = from;
        this.enabled = enabled;
    }

    public boolean available() {
        return enabled && !from.isBlank() && transport.configured();
    }

    public void sendTemplate(String to, String subject, String text, String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        deliver(new EmailMessage(from, to, subject, text, templates.process(template, context)));
    }

    public void sendText(String to, String subject, String text) {
        deliver(new EmailMessage(from, to, subject, text,
                "<html><body><pre style=\"white-space:pre-wrap\">" + HtmlUtils.htmlEscape(text) + "</pre></body></html>"));
    }

    private void deliver(EmailMessage message) {
        if (!enabled) throw MailDeliveryException.providerMisconfigured("Mail delivery is disabled");
        for (int attempt = 1; ; attempt++) {
            try {
                transport.send(message);
                return;
            } catch (MailDeliveryException ex) {
                if (!ex.isRetryable() || attempt == MAX_DELIVERY_ATTEMPTS) throw ex;
                // Never log recipients, message bodies, OTPs, credentials or provider response bodies.
                log.warn("Transient email delivery failure on attempt {}/{}; retrying", attempt, MAX_DELIVERY_ATTEMPTS);
            }
        }
    }
}
