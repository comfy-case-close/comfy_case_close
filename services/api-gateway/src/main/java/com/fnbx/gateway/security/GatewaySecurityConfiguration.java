package com.fnbx.gateway.security;

import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import com.fnbx.shared.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@Import(JwtConfiguration.class)
public class GatewaySecurityConfiguration {
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(SecretKey key, JwtSettings settings) {
        var decoder = NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtConfiguration.validator(settings, "access"));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http, GatewayErrorHandler errors,
            @Value("${fnb.security.cors.allowed-origins:${CORS_ALLOWED_ORIGINS:http://localhost:3000}}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        if (allowed.stream().anyMatch(s -> s.contains("*"))) throw new IllegalArgumentException("Explicit CORS origins are required");
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowed);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Platform-Key"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return http.cors(c -> c.configurationSource(source))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(auth -> {
                    auth.pathMatchers(HttpMethod.POST, SecurityRoutes.PUBLIC_AUTH_PATHS).permitAll();
                    auth.pathMatchers(HttpMethod.POST, SecurityRoutes.PUBLIC_SUBMIT_PATHS).permitAll();
                    // Identity's PlatformKeyFilter authenticates these exact routes.
                    for (SecurityRoutes.Route route : SecurityRoutes.PLATFORM_ROUTES) {
                        auth.pathMatchers(HttpMethod.valueOf(route.method()), route.pattern()).permitAll();
                    }
                    auth.anyExchange().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> {})).build();
    }
}
