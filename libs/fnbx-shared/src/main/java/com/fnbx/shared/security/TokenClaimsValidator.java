package com.fnbx.shared.security;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

/** No database lookup: verify token purpose and the claims used for tenant authorization. */
public final class TokenClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private final String type;
    public TokenClaimsValidator(String type) { this.type = type; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            if (!type.equals(jwt.getClaimAsString("type")) || jwt.getIssuedAt() == null
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || jwt.getSubject() == null || jwt.getSubject().isBlank()) throw new IllegalArgumentException();
            UUID.fromString(jwt.getId());
            UUID.fromString(jwt.getClaimAsString("uid"));
            UUID.fromString(jwt.getClaimAsString("business_id"));
            if (type.equals("refresh")) {
                Object version = jwt.getClaim("refresh_version");
                if (!(version instanceof Long || version instanceof Integer) || ((Number) version).longValue() < 0) {
                    throw new IllegalArgumentException();
                }
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException malformed) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
        }
    }
}
