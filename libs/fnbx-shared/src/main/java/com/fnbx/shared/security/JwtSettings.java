package com.fnbx.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Vakot's lifetimes and rotation grace, shared by issuer and resource servers. */
@ConfigurationProperties("fnb.security.jwt")
public record JwtSettings(String issuer, Long accessExpirationMs, Long refreshExpirationMs,
                          Long refreshRotationGraceSeconds) {
    public JwtSettings {
        issuer = issuer == null ? "fnbx-identity" : issuer;
        accessExpirationMs = accessExpirationMs == null ? 900_000L : accessExpirationMs;
        refreshExpirationMs = refreshExpirationMs == null ? 604_800_000L : refreshExpirationMs;
        refreshRotationGraceSeconds = refreshRotationGraceSeconds == null ? 15L : refreshRotationGraceSeconds;
        if (issuer.isBlank() || accessExpirationMs <= 0 || refreshExpirationMs <= accessExpirationMs
                || refreshRotationGraceSeconds < 0) {
            throw new IllegalArgumentException("Invalid FNB JWT settings");
        }
    }
}
