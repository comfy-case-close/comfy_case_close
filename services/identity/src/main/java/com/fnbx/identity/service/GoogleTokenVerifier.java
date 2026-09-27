package com.fnbx.identity.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fnbx.identity.exception.AuthExceptions;

/**
 * Verifies Google ID tokens the modern way: their RS256 signature is checked against Google's
 * published JWKS (fetched lazily and cached by Nimbus), and the timestamp is enforced, plus we
 * require the token's issuer to be Google and its audience to be our own OAuth client id. This is a
 * genuine cryptographic verification — unlike the reference implementation, which trusted a
 * base64-decoded payload. No token ever authenticates unless Google actually signed it for us.
 */
@Service
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private volatile JwtDecoder decoder; // built lazily so startup/tests never touch the network

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenVerifier(@Value("${fnb.google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    GoogleTokenVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
    }

    /** Verifies {@code idToken}; throws {@code AppException(INVALID_GOOGLE_TOKEN)} if it is not valid for this app. */
    public GoogleUser verify(String idToken) {
        if (!StringUtils.hasText(clientId)) {
            // Google login is not configured for this environment.
            throw AuthExceptions.invalidGoogleToken();
        }

        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException ex) {
            throw AuthExceptions.invalidGoogleToken();
        }

        String issuer = jwt.getClaimAsString("iss");
        if (issuer == null || !GOOGLE_ISSUERS.contains(issuer)) {
            throw AuthExceptions.invalidGoogleToken();
        }
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email) || email.indexOf('@') <= 0 || jwt.getExpiresAt() == null
                || !StringUtils.hasText(jwt.getSubject())
                || !Boolean.TRUE.equals(readBoolean(jwt.getClaim("email_verified")))) {
            throw AuthExceptions.invalidGoogleToken();
        }

        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        // FNB's requested name order: firstName = Ho, lastName = Viet Bach.
        String firstName = StringUtils.hasText(familyName) ? familyName.trim()
                : StringUtils.hasText(givenName) ? givenName.trim() : email.substring(0, email.indexOf('@'));
        String lastName = StringUtils.hasText(familyName) && StringUtils.hasText(givenName) ? givenName.trim() : "";
        return new GoogleUser(email, firstName, lastName, jwt.getClaimAsString("picture"));
    }

    private JwtDecoder decoder() {
        JwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                local = decoder;
                if (local == null) {
                    NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI)
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .build();
                    built.setJwtValidator(validators(clientId));
                    decoder = local = built;
                }
            }
        }
        return local;
    }

    static OAuth2TokenValidator<Jwt> validators(String clientId) {
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience mismatch", null));
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), audience);
    }

    private static Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return null;
    }

    /** The verified subset of Google profile claims we provision an account from. */
    public record GoogleUser(String email, String firstName, String lastName, String avatarUrl) {}
}
