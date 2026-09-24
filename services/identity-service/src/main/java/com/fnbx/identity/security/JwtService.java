package com.fnbx.identity.security;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import com.fnbx.identity.entity.AuthAccount;

import com.fnbx.shared.security.JwtConfiguration;
import com.fnbx.shared.security.JwtSettings;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

/** Vakot HS256 tokens, with FNB's signed tenant and per-branch role claims. */
@Component
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final JwtSettings settings;
    private final Clock clock;

    public JwtService(SecretKey key, JwtSettings settings, Clock clock) {
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        refreshDecoder = JwtConfiguration.decoder(key, settings, "refresh");
        this.settings = settings; this.clock = clock;
    }

    public String access(AuthAccount account) { return encode(account, "access", settings.accessExpirationMs()); }
    public String refresh(AuthAccount account) { return encode(account, "refresh", settings.refreshExpirationMs()); }
    public Jwt decodeRefresh(String token) { return refreshDecoder.decode(token); }

    private String encode(AuthAccount account, String type, long lifetime) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(settings.issuer()).subject(account.employeeCode())
                .issuedAt(now).expiresAt(now.plusMillis(lifetime)).id(UUID.randomUUID().toString())
                .claim("type", type).claim("uid", account.staffId().toString())
                .claim("business_id", account.businessId().toString())
                .claim("refresh_version", account.refreshVersion()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
