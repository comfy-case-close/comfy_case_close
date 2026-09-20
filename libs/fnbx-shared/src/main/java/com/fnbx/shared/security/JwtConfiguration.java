package com.fnbx.shared.security;

import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSettings.class)
public class JwtConfiguration {
    @Bean
    public Clock authClock() { return Clock.systemUTC(); }

    /** Sinh mot secret hop le: {@code openssl rand -base64 33} (44 ky tu, KHONG co dau '='). */
    private static final String HOW_TO_GENERATE = "generate one with: openssl rand -base64 33";

    @Bean
    public SecretKey jwtSecretKey(@Value("${fnb.security.jwt.secret:${JWT_SECRET:}}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT_SECRET is not set. Expected base64 of at least 32 random bytes - " + HOW_TO_GENERATE);
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must be base64, but " + describeFirstIllegalChar(secret)
                    + " (value length " + secret.length() + "). A stray escape such as '\\=' - IntelliJ"
                    + " adds one when the value is edited in the inline \"NAME=value;NAME2=value2\""
                    + " field - is the usual cause. Otherwise " + HOW_TO_GENERATE,
                    ex);
        }
        if (key.length < 32) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must contain at least 32 random bytes, but decoded to only "
                    + key.length + " - " + HOW_TO_GENERATE);
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    /**
     * Chi ra ky tu dau tien khong thuoc bang chu cai base64, de log noi RO cho dev
     * thay sai o dau. KHONG BAO GIO in ca secret ra log; ky tu bao cao o day theo
     * dinh nghia la ky tu RAC, khong phai mot phan cua secret that.
     */
    private static String describeFirstIllegalChar(String secret) {
        for (int i = 0; i < secret.length(); i++) {
            char c = secret.charAt(i);
            boolean legal = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
            if (!legal) {
                return String.format("index %d is %s (U+%04X)", i,
                        Character.isISOControl(c) ? "a control character" : "'" + c + "'", (int) c);
            }
        }
        return "its length or '=' padding is malformed";
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key, JwtSettings settings) {
        return decoder(key, settings, "access");
    }

    public static NimbusJwtDecoder decoder(SecretKey key, JwtSettings settings, String type) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(validator(settings, type));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validator(JwtSettings settings, String type) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(settings.issuer()),
                new TokenClaimsValidator(type));
    }
}
