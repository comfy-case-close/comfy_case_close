package com.fnbx.gateway.logging;

import com.fnbx.shared.logging.RequestIds;
import org.slf4j.*;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reactive boundary logging. Never leaves thread-local MDC across an asynchronous boundary. */
public class GatewayRequestLoggingFilter implements WebFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewayRequestLoggingFilter.class);
    public static final String REQUEST_ID_ATTRIBUTE = GatewayRequestLoggingFilter.class.getName() + ".requestId";
    private final String serviceName;

    public GatewayRequestLoggingFilter(String serviceName) { this.serviceName = serviceName; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            String requestId = RequestIds.resolve(exchange.getRequest().getHeaders().getFirst(RequestIds.HEADER));
            exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
            ServerWebExchange forwarded = exchange.mutate().request(request -> request.headers(headers ->
                    headers.set(RequestIds.HEADER, requestId))).build();
            long started = System.nanoTime();
            AtomicBoolean completed = new AtomicBoolean();
            withRequestId(requestId, () -> log.debug("Request started service={} method={}",
                    serviceName, exchange.getRequest().getMethod()));
            exchange.getResponse().getHeaders().set(RequestIds.HEADER, requestId);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(RequestIds.HEADER, requestId);
                if (completed.compareAndSet(false, true)) {
                    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    withRequestId(requestId, () -> log.info(
                            "Request completed method={} route={} status={} durationMs={} service={}",
                            exchange.getRequest().getMethod(), route == null ? "<unmapped>" : route.getId(),
                            exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value(),
                            (System.nanoTime() - started) / 1_000_000, serviceName));
                }
                return Mono.empty();
            });
            return chain.filter(forwarded).doFinally(signal -> {
                if (signal == SignalType.CANCEL && completed.compareAndSet(false, true)) {
                    withRequestId(requestId, () -> log.debug("Request cancelled service={} method={} durationMs={}",
                            serviceName, exchange.getRequest().getMethod(), (System.nanoTime() - started) / 1_000_000));
                }
            });
        });
    }

    public static void withRequestId(String requestId, Runnable action) {
        String previous = MDC.get("requestId");
        try {
            if (requestId == null) MDC.remove("requestId"); else MDC.put("requestId", requestId);
            action.run();
        } finally {
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }
}
