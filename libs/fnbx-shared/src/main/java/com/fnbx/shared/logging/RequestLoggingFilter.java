package com.fnbx.shared.logging;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Wraps security and MVC so rejected requests are visible too. Delegates optional DEBUG payload events to the shared policy. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = RequestIds.HEADER;
    private final String serviceName;

    private final ServletPayloadLogging payloadLogging;
    public RequestLoggingFilter(String serviceName) { this(serviceName, null); }
    public RequestLoggingFilter(String serviceName, ServletPayloadLogging payloadLogging) {
        this.serviceName = serviceName;
        this.payloadLogging = payloadLogging;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = RequestIds.resolve(request.getHeader(REQUEST_ID_HEADER));
        String previous = MDC.get("requestId");
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            log.debug("Request started service={} method={}", serviceName, request.getMethod());
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failed = true;
            throw ex;
        } finally {
            try {
                if (payloadLogging != null) payloadLogging.fallback(request, response);
                // Prefer the route template: unknown paths and user-controlled path text stay out of logs.
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                log.info("Request completed method={} route={} status={} durationMs={} service={}",
                        request.getMethod(), route == null ? "<unmapped>" : route,
                        failed ? 500 : response.getStatus(), (System.nanoTime() - started) / 1_000_000, serviceName);
            } finally {
                if (previous == null) MDC.remove("requestId");
                else MDC.put("requestId", previous);
            }
        }
    }
}
