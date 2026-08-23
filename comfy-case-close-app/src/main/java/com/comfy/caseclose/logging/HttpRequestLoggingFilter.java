package com.comfy.caseclose.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-ui")
                || "/swagger-ui.html".equals(uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = resolveRequestId(request);
        Throwable failure = null;

        MDC.put(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("httpPath", request.getRequestURI());
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        log.info("HTTP -> method={} path={} query={} remote={} user={}",
                request.getMethod(), request.getRequestURI(), sanitizeQuery(request.getQueryString()),
                request.getRemoteAddr(), currentUser());

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (failure == null) {
                log.info("HTTP <- method={} path={} status={} durationMs={} user={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, currentUser());
            } else {
                log.warn("HTTP !! method={} path={} status={} durationMs={} exception={} message={} user={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs,
                        failure.getClass().getSimpleName(), LoggingValueSanitizer.sanitizeMessage(failure.getMessage()),
                        currentUser());
            }
            MDC.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.length() > 80 ? requestId.substring(0, 80) : requestId;
    }

    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String[] parts = query.split("&");
        for (int i = 0; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = parts[i].substring(0, equals).toLowerCase(Locale.ROOT);
            if (LoggingValueSanitizer.isSensitiveName(key)) {
                parts[i] = parts[i].substring(0, equals + 1) + "***";
            }
        }
        return String.join("&", parts);
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
