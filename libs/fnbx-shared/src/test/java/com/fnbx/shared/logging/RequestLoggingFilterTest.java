package com.fnbx.shared.logging;

import java.util.UUID;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter("test-service");

    @AfterEach void clear() { MDC.clear(); }

    @Test void correlatesRequestAndLogsStatusWithoutQueryOrRawPath(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/cash-closes/private-id/submit");
        request.setQueryString("token=secret-query");
        request.addHeader("X-Request-ID", "example-request-123");
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-context");
        MDC.put("unrelated", "preserved");

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("example-request-123");
            assertThat(response.getHeader("X-Request-ID")).isEqualTo("example-request-123");
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/cash-closes/{id}/submit");
            response.setStatus(422);
        });

        assertThat(output).contains("method=POST route=/cash-closes/{id}/submit status=422 durationMs=")
                .doesNotContain("secret-query", "private-id");
        assertThat(MDC.get("requestId")).isEqualTo("outer-context");
        assertThat(MDC.get("unrelated")).isEqualTo("preserved");
    }

    @Test void replacesUnsafeRequestIdAndIncludesSecurityRejections(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/cash-closes");
        request.addHeader("X-Request-ID", "unsafe\r\nforged log");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> response.setStatus(401));

        assertThatCode(() -> UUID.fromString(response.getHeader("X-Request-ID"))).doesNotThrowAnyException();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(output).contains("route=<unmapped> status=401").doesNotContain("forged log");
    }

    @Test void logsServerFailureAndClearsContextWhenTheChainThrows(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/cash-closes");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("private failure detail");
        })).isInstanceOf(ServletException.class);

        assertThat(output).contains("status=500").doesNotContain("private failure detail");
        assertThat(MDC.get("requestId")).isNull();
    }
}
