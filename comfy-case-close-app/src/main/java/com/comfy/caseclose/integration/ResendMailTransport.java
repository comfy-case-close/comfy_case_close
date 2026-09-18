package com.comfy.caseclose.integration;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Resend HTTP transport adapted from Vakot_BE, with bounded connect/read timeouts. */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "resend")
public class ResendMailTransport implements MailTransport {

    private static final String API_BASE_URL = "https://api.resend.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public ResendMailTransport(@Value("${app.mail.resend.api-key:}") String apiKey) {
        this(RestClient.builder().requestFactory(timeBoundedRequestFactory()), apiKey);
    }

    /** Package-private: lets tests bind a {@code MockRestServiceServer} to the builder. */
    ResendMailTransport(RestClient.Builder builder, String apiKey) {
        this.restClient = builder.baseUrl(API_BASE_URL).build();
        this.apiKey = apiKey;
    }

    @Override
    public void send(EmailMessage message) {
        if (!StringUtils.hasText(apiKey)) {
            throw MailDeliveryException.providerMisconfigured("app.mail.resend.api-key is not set");
        }
        if (!StringUtils.hasText(message.from())) {
            throw MailDeliveryException.providerMisconfigured("Mail sender address is not configured");
        }
        try {
            restClient
                    .post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmail(
                            message.from(), List.of(message.to()), message.subject(), message.text(), message.html()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Do not retain provider response bodies: they may contain addresses or credentials.
            String detail = "Resend answered HTTP " + ex.getStatusCode().value();
            if (isWorthRetrying(ex.getStatusCode())) {
                throw MailDeliveryException.retryable(detail);
            }
            // Authentication/domain failures are configuration errors; other 4xx reject this message.
            throw ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                            || ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                    ? MailDeliveryException.providerMisconfigured(detail)
                    : MailDeliveryException.rejectedMessage(detail);
        } catch (RestClientException ex) {
            // No status at all: a connect or read timeout, DNS, a dropped connection. All transient.
            throw MailDeliveryException.retryable("Resend delivery failed");
        }
    }

    /**
     * A refusal worth repeating. 5xx is Resend having a bad moment and 429 is a rate limit that
     * lifts; other 4xx responses are statements about this message that a second identical request cannot
     * change.
     */
    private static boolean isWorthRetrying(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }

    private static SimpleClientHttpRequestFactory timeBoundedRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /** Request body of {@code POST /emails}; field names are Resend's. */
    private record ResendEmail(String from, List<String> to, String subject, String text, String html) {}
}
