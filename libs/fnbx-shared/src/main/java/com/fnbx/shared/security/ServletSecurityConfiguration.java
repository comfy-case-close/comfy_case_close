package com.fnbx.shared.security;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

/** Imported by every servlet service so calling a service directly cannot bypass authentication. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@Import(JwtConfiguration.class)
public class ServletSecurityConfiguration {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Authentication does not confer any role globally. Check AccessPrincipal at the resource's branch.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of());
        return converter;
    }

    @Bean
    public SecurityFilterChain serviceSecurity(HttpSecurity http, ObjectMapper mapper, JwtAuthenticationConverter converter,
            @Value("${spring.application.name:}") String applicationName,
            @Value("${fnb.security.cors.allowed-origins:${CORS_ALLOWED_ORIGINS:http://localhost:3000}}") String origins) throws Exception {
        ServletSecurityErrorHandler errors = new ServletSecurityErrorHandler(mapper);
        http.cors(cors -> cors.configurationSource(corsSource(origins)))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if ("identity-service".equals(applicationName)) {
                        auth.requestMatchers(HttpMethod.POST, SecurityRoutes.PUBLIC_AUTH_PATHS).permitAll();
                        auth.requestMatchers(HttpMethod.POST, SecurityRoutes.PUBLIC_SUBMIT_PATHS).permitAll();
                        auth.requestMatchers(SecurityRoutes.PUBLIC_SWAGGER_API_DOCS_PATHS).permitAll();
                        // NOT public. Exempt from bearer authentication because they carry the
                        // platform key instead, which identity's PlatformKeyFilter verifies on
                        // exactly this list. Neither half is safe without the other.
                        for (SecurityRoutes.Route route : SecurityRoutes.PLATFORM_ROUTES) {
                            auth.requestMatchers(HttpMethod.valueOf(route.method()), route.pattern()).permitAll();
                        }
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterAfter(new VerifiedTenantFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    public static CorsConfiguration cors(String origins) {
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        if (allowed.stream().anyMatch(s -> s.contains("*"))) throw new IllegalArgumentException("Explicit CORS origins are required");
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowed);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Platform-Key"));
        cors.setAllowCredentials(false); // Vakot contract: bearer JSON, no authentication cookies.
        cors.setMaxAge(3600L);
        return cors;
    }

    private static CorsConfigurationSource corsSource(String origins) {
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors(origins));
        return source;
    }
}
