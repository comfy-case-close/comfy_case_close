package com.fnbx.identity.service;

import java.time.Clock;
import java.util.UUID;
import com.fnbx.identity.entity.BusinessRegistration;
import com.fnbx.identity.repository.BusinessRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fnbx.mail.EmailService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationMailerTest {
    @Test void approvalEmailContainsUsableCredentialAndChangeInstructionButNoBusinessUuid() {
        EmailService sender = mock(EmailService.class);
        var transactions = mock(TenantTransactions.class);
        var events = mock(org.springframework.context.ApplicationEventPublisher.class);
        var mailer = new RegistrationMailer(events);
        var listener = new RegistrationEmailListener(sender, mock(BusinessRegistrationRepository.class), transactions,
                new MailHealth(), Clock.systemUTC(), Runnable::run);
        var registration = mock(BusinessRegistration.class);
        when(registration.registrationId()).thenReturn(UUID.randomUUID());
        when(registration.ownerEmail()).thenReturn("owner@example.test");
        when(registration.ownerFirstName()).thenReturn("Owner");
        when(registration.businessName()).thenReturn("Comfy");
        when(registration.businessCode()).thenReturn("COMFY-7K3M9X2Q8R4T");
        UUID businessId = UUID.randomUUID();
        when(registration.createdBusinessId()).thenReturn(businessId);
        String password = UUID.randomUUID().toString();

        mailer.approved(registration, password);

        var event = ArgumentCaptor.forClass(RegistrationEmailRequested.class);
        verify(events).publishEvent(event.capture());
        listener.onRegistrationEmailRequested(event.getValue());
        var message = ArgumentCaptor.forClass(String.class);
        verify(sender).sendText(eq("owner@example.test"), contains("approved"), message.capture());
        assertThat(message.getValue()).contains("COMFY-7K3M9X2Q8R4T", password,
                "change your password immediately", "Forgot Password")
                .doesNotContain(businessId.toString(), "Business ID", "not active yet");
    }
}
