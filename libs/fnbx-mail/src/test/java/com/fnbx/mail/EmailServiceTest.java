package com.fnbx.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    MailTransport transport = mock(MailTransport.class);
    EmailService email = new EmailService(transport, new MailConfiguration().emailTemplateEngine(), "sender@example.test", true);

    @Test void transientFailuresRetryButPermanentFailuresDoNot() {
        doThrow(MailDeliveryException.retryable("timeout")).doThrow(MailDeliveryException.retryable("timeout"))
                .doNothing().when(transport).send(any());
        email.sendText("recipient@example.test", "Subject", "Body");
        verify(transport, times(3)).send(any());
        reset(transport);
        doThrow(MailDeliveryException.providerMisconfigured("key")).when(transport).send(any());
        assertThatThrownBy(() -> email.sendText("recipient@example.test", "Subject", "Body"))
                .isInstanceOf(MailDeliveryException.class);
        verify(transport).send(any());
    }

    @Test void retriesAreBoundedAndBodiesAreEscaped() {
        doThrow(MailDeliveryException.retryable("timeout")).when(transport).send(any());
        assertThatThrownBy(() -> email.sendText("recipient@example.test", "Subject", "<script>secret</script>"))
                .isInstanceOf(MailDeliveryException.class);
        var messages = ArgumentCaptor.forClass(EmailMessage.class);
        verify(transport, times(3)).send(messages.capture());
        assertThat(messages.getValue().html()).contains("&lt;script&gt;").doesNotContain("<script>");
        assertThat(messages.getValue().text()).isEqualTo("<script>secret</script>");
    }

    @Test void readinessUsesSelectedTransportAndDisabledDeliveryNeverSends() {
        when(transport.configured()).thenReturn(true);
        assertThat(email.available()).isTrue();
        EmailService disabled = new EmailService(transport, new MailConfiguration().emailTemplateEngine(), "sender@example.test", false);
        assertThat(disabled.available()).isFalse();
        assertThatThrownBy(() -> disabled.sendText("recipient@example.test", "Subject", "Body"))
                .isInstanceOf(MailDeliveryException.class);
        verify(transport, never()).send(any());
    }
}
