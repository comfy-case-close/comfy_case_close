package com.fnbx.gateway.logging;

import java.time.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class GatewayRequestLoggingFilterTest {
    private final GatewayRequestLoggingFilter filter = new GatewayRequestLoggingFilter("gateway-test");
    @AfterEach void clear() { MDC.clear(); }

    @Test void finalErrorStatusIsLoggedAfterTheOuterErrorHandlerCommits(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/private-path?token=secret-query")
                .header("X-Request-ID", "error-example"));
        filter.filter(exchange, forwarded -> Mono.error(new IllegalStateException("secret-error")))
                .onErrorResume(error -> {
                    exchange.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
                    return exchange.getResponse().setComplete();
                }).block();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-ID")).isEqualTo("error-example");
        assertThat(output).contains("Request completed method=GET route=<unmapped> status=502")
                .doesNotContain("private-path", "secret-query", "secret-error");
        assertThat(output.getAll().split("Request completed", -1)).hasSize(2);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void threadSwitchesDoNotLeakThreadLocalMdcAndHeaderIsForwarded(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/private-path")
                .header("X-Request-ID", "async-example"));
        MDC.put("requestId", "caller-context");
        filter.filter(exchange, forwarded -> {
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Request-ID")).isEqualTo("async-example");
            return Mono.delay(Duration.ofMillis(1), Schedulers.boundedElastic())
                    .then(Mono.defer(() -> {
                        assertThat(MDC.get("requestId")).isNull();
                        return forwarded.getResponse().setComplete();
                    })).then(Mono.fromRunnable(() -> assertThat(MDC.get("requestId")).isNull()));
        }).block();
        assertThat(MDC.get("requestId")).isEqualTo("caller-context");
        assertThat(output).contains("status=200", "service=gateway-test");
    }

    @Test void nestedLoggingRestoresPreviouslyInstalledMdcEvenOnFailure() {
        MDC.put("requestId", "outer");
        assertThatThrownBy(() -> GatewayRequestLoggingFilter.withRequestId("inner", () -> {
            assertThat(MDC.get("requestId")).isEqualTo("inner");
            throw new IllegalStateException();
        })).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("requestId")).isEqualTo("outer");
    }
}
