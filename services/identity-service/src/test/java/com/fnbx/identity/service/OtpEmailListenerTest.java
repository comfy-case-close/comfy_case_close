package com.fnbx.identity.service;

import com.fnbx.identity.utils.enums.OtpPurpose;
import com.fnbx.mail.EmailService;
import com.fnbx.mail.MailDeliveryException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtpEmailListenerTest {
    @Test void allOtpPurposesUseSharedMailAndFailuresInvalidateTheCode() {
        for (OtpPurpose purpose : OtpPurpose.values()) {
            var store = new VerificationStore();
            var email = mock(EmailService.class);
            var health = new MailHealth();
            var listener = new OtpEmailListener(email, store, health, Runnable::run);
            String code = store.issueOtp(purpose, "tenant:recipient");
            listener.onOtpRequested(new OtpEmailRequested(purpose, "tenant:recipient", "recipient@example.test", code));
            verify(email).sendText(eq("recipient@example.test"), anyString(), contains(code));
            store.consumeOtp(purpose, "tenant:recipient", code);
            String failedCode = store.issueOtp(purpose, "tenant:recipient");
            doThrow(MailDeliveryException.providerMisconfigured("bad key")).when(email).sendText(any(), any(), any());
            listener.onOtpRequested(new OtpEmailRequested(purpose, "tenant:recipient", "recipient@example.test", failedCode));
            assertThatThrownBy(() -> store.consumeOtp(purpose, "tenant:recipient", failedCode)).isInstanceOf(RuntimeException.class);
            assertThat(health.isDown()).isTrue();
            assertThat(store.issueOtp(purpose, "tenant:recipient")).isNotBlank();
        }
    }

    @Test void delayedFailureLeavesNewerCodeIntact() {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var store = new VerificationStore(clock);
        var old = store.issueOtp(OtpPurpose.SIGNUP, "tenant:recipient");
        when(clock.instant()).thenReturn(now.plusSeconds(120));
        var fresh = store.issueOtp(OtpPurpose.SIGNUP, "tenant:recipient");
        // Use a different undelivered value so random six-digit equality cannot make the test flaky.
        store.discardOtp(OtpPurpose.SIGNUP, "tenant:recipient", fresh.equals(old) ? "invalid" : old);
        assertThatCode(() -> store.consumeOtp(OtpPurpose.SIGNUP, "tenant:recipient", fresh)).doesNotThrowAnyException();
    }

    @Test void facadePublishesAndReportsProviderReadinessAndQueueRejection() {
        var email = mock(EmailService.class);
        var events = mock(ApplicationEventPublisher.class);
        var facade = new OtpMailer(email, new MailHealth(), events);
        when(email.available()).thenReturn(true);
        assertThat(facade.available()).isTrue();
        facade.mail(OtpPurpose.PASSWORD_RESET, "scope", "to@example.test", "123456");
        verify(events).publishEvent(new OtpEmailRequested(OtpPurpose.PASSWORD_RESET, "scope", "to@example.test", "123456"));
        var listener = new OtpEmailListener(email, new VerificationStore(), new MailHealth(),
                task -> { throw new RejectedExecutionException(); });
        assertThatThrownBy(() -> listener.onOtpRequested(new OtpEmailRequested(OtpPurpose.SIGNUP, "scope", "to@example.test", "123456")))
                .isInstanceOf(RejectedExecutionException.class);
        verify(email, never()).sendText(any(), any(), any());
    }
}
