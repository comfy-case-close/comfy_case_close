package com.comfy.caseclose.integration;

import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpMailTransportTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ObjectProvider<JavaMailSender> provider = provider();
    private final SmtpMailTransport transport = new SmtpMailTransport(provider);
    private final EmailMessage message = new EmailMessage("sender@example.com", "lead@example.com",
            "TX - MORNING_CLOSE", "Tâm submitted", "<p>Tâm submitted</p>");

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider() {
        return mock(ObjectProvider.class);
    }

    private MimeMessage configureSender() {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(mime);
        return mime;
    }

    @Test
    void buildsUtf8MultipartAlternativeWithOneRecipient() throws Exception {
        MimeMessage mime = configureSender();
        transport.send(message);
        mime.saveChanges();
        verify(sender).send(mime);
        assertThat(mime.getAllRecipients()).hasSize(1);
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("lead@example.com");
        assertThat(mime.getFrom()[0].toString()).isEqualTo("sender@example.com");
        assertThat(mime.getSubject()).isEqualTo(message.subject());
        assertThat(hasBody(mime, "text/plain", message.text())).isTrue();
        assertThat(hasBody(mime, "text/html", message.html())).isTrue();
    }

    private boolean hasBody(Part part, String type, String body) throws Exception {
        if (part.isMimeType(type)) {
            return body.equals(part.getContent()) && part.getContentType().toUpperCase().contains("UTF-8");
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                if (hasBody(multipart.getBodyPart(i), type, body)) return true;
            }
        }
        return false;
    }

    @Test
    void missingSmtpIsPermanentConfigurationFailure() {
        assertThatThrownBy(() -> transport.send(message)).isInstanceOfSatisfying(MailDeliveryException.class, ex -> {
            assertThat(ex.isRetryable()).isFalse();
            assertThat(ex.isProviderFault()).isTrue();
        });
    }

    @Test
    void invalidAddressIsNotRetried() {
        configureSender();
        assertThatThrownBy(() -> transport.send(new EmailMessage(message.from(), "invalid", "subject", "text", "html")))
                .isInstanceOfSatisfying(MailDeliveryException.class, ex -> assertThat(ex.isRetryable()).isFalse());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void authenticationFailureIsPermanent() {
        configureSender();
        doThrow(new MailAuthenticationException("secret provider detail")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> transport.send(message)).isInstanceOfSatisfying(MailDeliveryException.class, ex -> {
            assertThat(ex.isRetryable()).isFalse();
            assertThat(ex.getMessage()).doesNotContain("secret provider detail");
        });
    }

    @Test
    void networkFailureIsRetryable() {
        configureSender();
        doThrow(new MailSendException("connection timed out")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> transport.send(message)).isInstanceOfSatisfying(MailDeliveryException.class,
                ex -> assertThat(ex.isRetryable()).isTrue());
    }
}
