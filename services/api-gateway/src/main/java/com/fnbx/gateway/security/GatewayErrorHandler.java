package com.fnbx.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.shared.exception.AppException;
import com.fnbx.gateway.logging.GatewayRequestLoggingFilter;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.exception.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Reactive equivalent of servlet advice and security handlers, using the shared error DTO. */
@Component
@Order(-2)
public class GatewayErrorHandler implements WebExceptionHandler, ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);
    private final ObjectMapper mapper;
    private final com.fnbx.shared.logging.HttpPayloadLogger payloadLogger;

    public GatewayErrorHandler(ObjectMapper mapper) { this.mapper = mapper; this.payloadLogger = null; }

    @org.springframework.beans.factory.annotation.Autowired
    public GatewayErrorHandler(ObjectMapper mapper,
            org.springframework.beans.factory.ObjectProvider<com.fnbx.shared.logging.HttpPayloadLogger> payloadLogger) {
        this.mapper = mapper; this.payloadLogger = payloadLogger.getIfAvailable();
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ErrorCode code = ex instanceof OAuth2AuthenticationException ? ErrorCode.INVALID_TOKEN : ErrorCode.UNAUTHENTICATED;
        logFailure(exchange, code, ex);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(exchange, code.getStatus(), ErrorResponse.of(code, path(exchange)));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        logFailure(exchange, ErrorCode.ACCESS_DENIED, ex);
        return write(exchange, ErrorCode.ACCESS_DENIED.getStatus(), ErrorResponse.of(ErrorCode.ACCESS_DENIED, path(exchange)));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted() || !(ex instanceof Exception)) return Mono.error(ex);
        if (ex instanceof AppException app) {
            logFailure(exchange, app.getErrorCode(), ex);
            return write(exchange, app.getErrorCode().getStatus(), ErrorResponse.of(app.getErrorCode(),
                    app.getMessage(), path(exchange), java.util.List.of()));
        }
        HttpStatusCode status = ex instanceof ResponseStatusException response ? response.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ResponseStatusException response) exchange.getResponse().getHeaders().putAll(response.getHeaders());
        logFailure(exchange, ErrorCode.forHttpStatus(status.value()), ex);
        return write(exchange, status, ErrorResponse.of(ErrorCode.forHttpStatus(status.value()), path(exchange)));
    }

    private void logFailure(ServerWebExchange exchange, ErrorCode code, Throwable failure) {
        GatewayRequestLoggingFilter.withRequestId(exchange.getAttribute(GatewayRequestLoggingFilter.REQUEST_ID_ATTRIBUTE), () -> {
            if (code.getStatus().is5xxServerError()) {
                log.error("Gateway request failed errorCode={} errorName={} exceptionType={}",
                        code.getCode(), code.name(), failure.getClass().getName());
            } else {
                log.warn("Request rejected errorCode={} errorName={}", code.getCode(), code.name());
            }
        });
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, ErrorResponse body) {
        return Mono.defer(() -> {
            try {
                byte[] bytes = mapper.writeValueAsBytes(body);
                exchange.getResponse().setStatusCode(status);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                if (payloadLogger != null && payloadLogger.active()) {
                    var redactor = payloadLogger.newRedactor();
                    var metadata = new java.util.LinkedHashMap<String, Object>();
                    metadata.put("method", exchange.getRequest().getMethod().name());
                    metadata.put("route", "<unmapped>");
                    metadata.put("headers", exchange.getRequest().getHeaders());
                    metadata.put("parameters", exchange.getRequest().getQueryParams());
                    GatewayRequestLoggingFilter.withRequestId(exchange.getAttribute(GatewayRequestLoggingFilter.REQUEST_ID_ATTRIBUTE), () -> {
                        payloadLogger.write("request", metadata, java.util.Map.of("omitted", "rejected_before_body_read"), redactor);
                        metadata.remove("parameters"); metadata.put("headers", exchange.getResponse().getHeaders());
                        metadata.put("status", status.value());
                        payloadLogger.write("response", metadata, body, redactor);
                    });
                }
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
            } catch (JsonProcessingException serialization) { return Mono.error(serialization); }
        });
    }

    private static String path(ServerWebExchange exchange) { return exchange.getRequest().getURI().getRawPath(); }
}
