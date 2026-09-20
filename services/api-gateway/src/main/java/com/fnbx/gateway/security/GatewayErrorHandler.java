package com.fnbx.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.shared.exception.AppException;
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

    public GatewayErrorHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ErrorCode code = ex instanceof OAuth2AuthenticationException ? ErrorCode.INVALID_TOKEN : ErrorCode.UNAUTHENTICATED;
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return write(exchange, code.getStatus(), ErrorResponse.of(code, path(exchange)));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        return write(exchange, ErrorCode.ACCESS_DENIED.getStatus(), ErrorResponse.of(ErrorCode.ACCESS_DENIED, path(exchange)));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted() || !(ex instanceof Exception)) return Mono.error(ex);
        if (ex instanceof AppException app) {
            return write(exchange, app.getErrorCode().getStatus(), ErrorResponse.of(app.getErrorCode(),
                    app.getMessage(), path(exchange), java.util.List.of()));
        }
        HttpStatusCode status = ex instanceof ResponseStatusException response ? response.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ResponseStatusException response) exchange.getResponse().getHeaders().putAll(response.getHeaders());
        if (status.is5xxServerError()) log.error("Gateway request failed: {}", ex.getClass().getName());
        return write(exchange, status, ErrorResponse.of(ErrorCode.forHttpStatus(status.value()), path(exchange)));
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, ErrorResponse body) {
        return Mono.defer(() -> {
            try {
                byte[] bytes = mapper.writeValueAsBytes(body);
                exchange.getResponse().setStatusCode(status);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
            } catch (JsonProcessingException serialization) { return Mono.error(serialization); }
        });
    }

    private static String path(ServerWebExchange exchange) { return exchange.getRequest().getURI().getRawPath(); }
}
