package com.fnbx.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "fnb.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class GatewaySecurityTest {
    private static final AtomicReference<String> FORWARDED_BUSINESS = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_USER = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_PLATFORM_KEY = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_BEARER = new AtomicReference<>();
    private static final DisposableServer IDENTITY = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                FORWARDED_BUSINESS.set(request.requestHeaders().get("X-Business-Id"));
                FORWARDED_USER.set(request.requestHeaders().get("X-User-Id"));
                FORWARDED_PLATFORM_KEY.set(request.requestHeaders().get("X-Platform-Key"));
                FORWARDED_BEARER.set(request.requestHeaders().get("Authorization"));
                return response.status(200).sendString(Mono.just("identity reached"));
            }).bindNow();
    @Autowired WebTestClient client;

    @DynamicPropertySource static void backend(DynamicPropertyRegistry registry) {
        registry.add("SVC_IDENTITY_URI", () -> "http://127.0.0.1:" + IDENTITY.port());
    }
    @AfterAll static void shutdown() { IDENTITY.disposeNow(); }

    @Test void allowsPublicAuthRoutesAndProtectsEverythingElse() {
        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/auth/refresh").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/auth/me").exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody().jsonPath("$.code").isEqualTo(2409).jsonPath("$.path").isEqualTo("/api/v1/auth/me");
        client.post().uri("/api/v1/cash-closes").header("X-Business-Id", UUID.randomUUID().toString())
                .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/auth/login").exchange().expectStatus().isUnauthorized();
    }

    @Test void routesRegistrationAndPlatformDecisionsToIdentityForAuthentication() {
        client.post().uri("/api/v1/businesses/registrations/start").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/businesses/registrations/verify").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/businesses/registrations").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/businesses/registrations/" + UUID.randomUUID() + "/approve")
                .header("X-Platform-Key", "platform-test-key").exchange().expectStatus().isOk();
        assertThat(FORWARDED_PLATFORM_KEY.get()).isEqualTo("platform-test-key");
        client.post().uri("/api/v1/businesses").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/businesses/lookup?code=TEST").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/businesses/me").exchange().expectStatus().isUnauthorized();
    }

    @Test void validatesTokenPurposeAndStripsUntrustedIdentityHeaders() {
        client.get().uri("/api/v1/auth/me").headers(h -> h.setBearerAuth(token("refresh")))
                .exchange().expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo(2407);
        String access = token("access");
        client.get().uri("/api/v1/auth/me").headers(h -> {
            h.setBearerAuth(access); h.set("X-Business-Id", "spoof"); h.set("X-User-Id", "spoof");
        }).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("identity reached");
        assertThat(FORWARDED_BUSINESS.get()).isNull();
        assertThat(FORWARDED_USER.get()).isNull();
        assertThat(FORWARDED_BEARER.get()).isEqualTo("Bearer " + access);
    }

    @Test void handlesOnlyAllowedCorsPreflights() {
        client.options().uri("/api/v1/auth/refresh").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
        client.options().uri("/api/v1/auth/refresh").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST").exchange().expectStatus().isForbidden();
    }

    @Test void unknownRoutesUseTheSharedErrorContract() {
        client.get().uri("/api/v1/unknown").headers(h -> h.setBearerAuth(token("access")))
                .exchange().expectStatus().isNotFound().expectBody()
                .jsonPath("$.code").isEqualTo(2002).jsonPath("$.message").isEqualTo("Resource not found")
                .jsonPath("$.fieldErrors").isArray();
    }

    private static String token(String type) {
        var claims = JwtClaimsSet.builder().issuer("fnbx-identity").subject("OWNER").id(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("type", type)
                .claim("uid", UUID.randomUUID().toString()).claim("business_id", UUID.randomUUID().toString())
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[32], "HmacSHA256")))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
