package com.comfy.caseclose.integration;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendMailTransportTest {
    private static final String ENDPOINT = "https://api.resend.com/emails";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final EmailMessage message = new EmailMessage("cash-close@example.com", "lead@example.com",
            "TX - MORNING_CLOSE", "Tâm submitted", "<p>Tâm submitted</p>");

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void postsBothBodiesAndOneRecipientWithBearerAuthentication() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test_key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value(message.from()))
                .andExpect(jsonPath("$.to.length()").value(1))
                .andExpect(jsonPath("$.to[0]").value(message.to()))
                .andExpect(jsonPath("$.subject").value(message.subject()))
                .andExpect(jsonPath("$.text").value(message.text()))
                .andExpect(jsonPath("$.html").value(message.html()))
                .andRespond(withSuccess("{\"id\":\"test-id\"}", MediaType.APPLICATION_JSON));
        transport("re_test_key").send(message);
    }

    @Test
    void missingKeyFailsBeforeCallingResend() {
        assertThatThrownBy(() -> transport("").send(message))
                .isInstanceOfSatisfying(MailDeliveryException.class, ex -> {
                    assertThat(ex.isRetryable()).isFalse();
                    assertThat(ex.isProviderFault()).isTrue();
                });
    }

    @Test
    void missingSenderFailsBeforeCallingResend() {
        assertThatThrownBy(() -> transport("re_test_key").send(
                new EmailMessage("", message.to(), message.subject(), message.text(), message.html())))
                .isInstanceOfSatisfying(MailDeliveryException.class, ex -> {
                    assertThat(ex.isRetryable()).isFalse();
                    assertThat(ex.isProviderFault()).isTrue();
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 422, 429, 500, 503})
    void classifiesHttpFailuresWithoutExposingProviderDetails(int status) {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"secret provider detail lead@example.com\"}"));
        assertThatThrownBy(() -> transport("re_test_key").send(message))
                .isInstanceOfSatisfying(MailDeliveryException.class, ex -> {
                    assertThat(ex.isRetryable()).isEqualTo(status == 429 || status >= 500);
                    assertThat(ex.isProviderFault()).isEqualTo(status == 401 || status == 403);
                    assertThat(ex.getMessage()).contains(Integer.toString(status))
                            .doesNotContain("secret provider detail", "lead@example.com", "re_test_key");
                    assertThat(ex.getCause()).isNull();
                });
    }

    @Test
    void networkFailuresAreRetryableWithoutExposingProviderDetails() {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new IOException("secret network detail")));
        assertThatThrownBy(() -> transport("re_test_key").send(message))
                .isInstanceOfSatisfying(MailDeliveryException.class, ex -> {
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getMessage()).doesNotContain("secret network detail");
                    assertThat(ex.getCause()).isNull();
                });
    }

    private ResendMailTransport transport(String apiKey) {
        return new ResendMailTransport(builder, apiKey);
    }
}
