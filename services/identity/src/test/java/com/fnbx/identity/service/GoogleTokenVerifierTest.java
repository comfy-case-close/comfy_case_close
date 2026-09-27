package com.fnbx.identity.service;

import java.time.Instant;
import java.util.List;
import com.fnbx.shared.exception.AppException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class GoogleTokenVerifierTest {
    @Test void verifiesSignatureAudienceIssuerExpiryAndEmailProof() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(GoogleTokenVerifier.validators("fnb-google-client"));
        var verifier = new GoogleTokenVerifier("fnb-google-client", decoder);
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        String valid = token(encoder, "https://accounts.google.com", "fnb-google-client", true, Instant.now().plusSeconds(300));
        assertThat(verifier.verify(valid).email()).isEqualTo("staff@example.test");
        assertThat(verifier.verify(valid).firstName()).isEqualTo("Ho");
        assertThat(verifier.verify(valid).lastName()).isEqualTo("Viet Bach");
        assertThat(verifier.verify(token(encoder, "accounts.google.com", "fnb-google-client", true,
                Instant.now().plusSeconds(300))).email()).isEqualTo("staff@example.test");
        for (String invalid : List.of(
                token(encoder, "https://attacker.example", "fnb-google-client", true, Instant.now().plusSeconds(300)),
                token(encoder, "https://accounts.google.com", "different-client", true, Instant.now().plusSeconds(300)),
                token(encoder, "https://accounts.google.com", "fnb-google-client", false, Instant.now().plusSeconds(300)),
                token(encoder, "https://accounts.google.com", "fnb-google-client", true, Instant.now().minusSeconds(120)),
                "not-a-token")) {
            assertThatThrownBy(() -> verifier.verify(invalid)).isInstanceOf(AppException.class);
        }
        RSAKey other = new RSAKeyGenerator(2048).keyID("test").generate();
        var otherEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(other)));
        assertThatThrownBy(() -> verifier.verify(token(otherEncoder, "https://accounts.google.com", "fnb-google-client", true,
                Instant.now().plusSeconds(300)))).isInstanceOf(AppException.class);
    }

    @Test void unconfiguredGoogleLoginIsDisabled() {
        assertThatThrownBy(() -> new GoogleTokenVerifier("").verify("anything")).isInstanceOf(AppException.class);
    }

    private static String token(JwtEncoder encoder, String issuer, String audience, boolean verified, Instant expiry) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(),
                JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("google-subject")
                        .issuedAt(Instant.now().minusSeconds(300)).expiresAt(expiry)
                        .claim("email", "staff@example.test").claim("email_verified", verified)
                        .claim("family_name", "Ho").claim("given_name", "Viet Bach").build())).getTokenValue();
    }
}
