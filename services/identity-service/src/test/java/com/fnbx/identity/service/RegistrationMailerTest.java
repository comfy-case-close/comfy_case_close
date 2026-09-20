package com.fnbx.identity.service;

import java.time.Clock;
import java.util.UUID;
import com.fnbx.identity.entity.BusinessRegistration;
import com.fnbx.identity.repository.BusinessRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationMailerTest {
    @Test void approvalEmailContainsUsableCredentialAndChangeInstructionButNoBusinessUuid() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(sender);
        var transactions = mock(TenantTransactions.class);
        var mailer = new RegistrationMailer(providers, mock(BusinessRegistrationRepository.class), transactions,
                new MailHealth(), Clock.systemUTC(), "noreply@example.test");
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

        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("owner@example.test");
        assertThat(message.getValue().getText()).contains("COMFY-7K3M9X2Q8R4T", password,
                "change your password immediately", "Forgot Password")
                .doesNotContain(businessId.toString(), "Business ID", "not active yet");
    }
}
