package com.fnbx.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MailProviderSelectionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SmtpMailTransport.class, ResendMailTransport.class);

    @Test
    void missingProviderSelectsOnlySmtp() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MailTransport.class);
            assertThat(context.getBean(MailTransport.class)).isInstanceOf(SmtpMailTransport.class);
        });
    }

    @Test
    void explicitSmtpSelectsOnlySmtp() {
        contextRunner.withPropertyValues("fnb.mail.provider=smtp").run(context -> {
            assertThat(context).hasSingleBean(MailTransport.class);
            assertThat(context.getBean(MailTransport.class)).isInstanceOf(SmtpMailTransport.class);
        });
    }

    @Test
    void resendSelectsOnlyResendWithoutSmtpConfiguration() {
        contextRunner.withPropertyValues("fnb.mail.provider=resend", "fnb.mail.resend.api-key=re_test_key")
                .run(context -> {
                    assertThat(context).hasSingleBean(MailTransport.class);
                    assertThat(context.getBean(MailTransport.class)).isInstanceOf(ResendMailTransport.class);
                });
    }

    @Test
    void unknownProviderDoesNotSilentlyFallBackToSmtp() {
        contextRunner.withPropertyValues("fnb.mail.provider=unknown")
                .run(context -> assertThat(context).doesNotHaveBean(MailTransport.class));
    }
}
