package com.fnbx.shared.security;

import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.fnbx.shared.enums.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class TokenSecurityTest {
    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "HmacSHA256");
    private final JwtSettings settings = new JwtSettings(null, null, null, null);
    private final UUID branch = UUID.randomUUID();

    @Test void verifiesAccessButNeverAcceptsARefreshTokenAsBearer() {
        var decoder = JwtConfiguration.decoder(key, settings, "access");
        assertThat(decoder.decode(token("access", "fnbx-identity", Instant.now().plusSeconds(900), true))).isNotNull();
        assertThatThrownBy(() -> decoder.decode(token("refresh", "fnbx-identity", Instant.now().plusSeconds(900), true)))
                .isInstanceOf(JwtException.class);
    }

    @Test void rejectsExpiryWrongIssuerMissingClaimsAndWrongSigningKey() {
        var decoder = JwtConfiguration.decoder(key, settings, "access");
        for (String invalid : List.of(token("access", "other-app", Instant.now().plusSeconds(900), true),
                token("access", "fnbx-identity", Instant.now().minusSeconds(120), true),
                token("access", "fnbx-identity", Instant.now().plusSeconds(900), false))) {
            assertThatThrownBy(() -> decoder.decode(invalid)).isInstanceOf(JwtException.class);
        }
        byte[] other = new byte[32]; Arrays.fill(other, (byte) 5);
        var wrongDecoder = JwtConfiguration.decoder(new SecretKeySpec(other, "HmacSHA256"), settings, "access");
        assertThatThrownBy(() -> wrongDecoder.decode(token("access", "fnbx-identity", Instant.now().plusSeconds(900), true)))
                .isInstanceOf(JwtException.class);
    }

    @Test void refreshDecoderRejectsAccessTokens() {
        assertThatThrownBy(() -> JwtConfiguration.decoder(key, settings, "refresh")
                .decode(token("access", "fnbx-identity", Instant.now().plusSeconds(900), true))).isInstanceOf(JwtException.class);
    }

    @Test void rolesAreScopedToEachBranch() {
        UUID staffBranch = UUID.randomUUID();
        var principal = new AccessPrincipal(UUID.randomUUID(), UUID.randomUUID(), Map.of(branch, UserRole.ADMIN, staffBranch, UserRole.STAFF));
        assertThatCode(() -> principal.requireBranch(branch, UserRole.ADMIN)).doesNotThrowAnyException();
        assertThatThrownBy(() -> principal.requireBranch(staffBranch, UserRole.ADMIN)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> principal.requireBranch(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThat(principal.branches(UserRole.ADMIN)).containsExactly(branch);
    }

    @Test void legacySummaryRoleAndBranchGrantsNeverBecomeGlobalAuthorities() {
        Jwt jwt = Jwt.withTokenValue("already-verified").header("alg", "HS256").subject("STAFF")
                .claim("uid", UUID.randomUUID().toString()).claim("business_id", UUID.randomUUID().toString())
                .claim("role", "ADMIN").claim("branch_roles", Map.of(branch.toString(), "STAFF")).build();
        var authentication = new ServletSecurityConfiguration().jwtAuthenticationConverter().convert(jwt);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).isEmpty();
        var principal = AccessPrincipal.from(jwt);
        assertThatCode(() -> principal.requireBranch(branch, UserRole.STAFF)).doesNotThrowAnyException();
        assertThatThrownBy(() -> principal.requireBranch(branch, UserRole.ADMIN)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void refusesUnconfiguredAndShortKeys() {
        assertThatThrownBy(() -> new JwtConfiguration().jwtSecretKey("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtConfiguration().jwtSecretKey("not-base64")).isInstanceOf(IllegalArgumentException.class);
    }

    private String token(String type, String issuer, Instant expiry, boolean grants) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("OWNER").issuedAt(Instant.now().minusSeconds(300))
                .expiresAt(expiry).id(UUID.randomUUID().toString()).claim("type", type)
                .claim("uid", UUID.randomUUID().toString()).claim("business_id", UUID.randomUUID().toString());
        if (grants) claims.claim("branch_roles", Map.of(branch.toString(), "ADMIN"));
        return new NimbusJwtEncoder(new ImmutableSecret<>(key)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
