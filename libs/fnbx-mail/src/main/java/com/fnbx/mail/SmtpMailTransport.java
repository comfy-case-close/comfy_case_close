package com.fnbx.mail;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Vakot's JavaMailSender transport, using Spring's standard spring.mail.* configuration. */
@Component
@ConditionalOnProperty(name = "fnb.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public SmtpMailTransport(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public boolean configured() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        return sender != null && (!(sender instanceof JavaMailSenderImpl smtp)
                || StringUtils.hasText(smtp.getHost()));
    }

    @Override
    public void send(EmailMessage message) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || (mailSender instanceof JavaMailSenderImpl smtp
                && !StringUtils.hasText(smtp.getHost()))) {
            throw MailDeliveryException.providerMisconfigured("SMTP host is not configured");
        }
        if (!StringUtils.hasText(message.from())) {
            throw MailDeliveryException.providerMisconfigured("Mail sender address is not configured");
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setValidateAddresses(true);
            helper.setFrom(message.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mimeMessage);
        } catch (MailAuthenticationException ex) {
            throw MailDeliveryException.providerMisconfigured("SMTP rejected the credentials");
        } catch (MailParseException | MessagingException | IllegalArgumentException ex) {
            throw MailDeliveryException.rejectedMessage("Message could not be built");
        } catch (MailException ex) {
            // Do not retain provider error text: it may contain recipient addresses or credentials.
            throw MailDeliveryException.retryable("SMTP delivery failed");
        }
    }
}
