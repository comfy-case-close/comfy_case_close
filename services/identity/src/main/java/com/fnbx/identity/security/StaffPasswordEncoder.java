package com.fnbx.identity.security;

import java.nio.charset.StandardCharsets;
import com.fnbx.identity.entity.AuthAccount;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** New credentials use Vakot's BCrypt; existing Argon2 credentials remain usable. */
@Component
public class StaffPasswordEncoder {
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final Argon2PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final String dummy = bcrypt.encode("dummy-password-for-missing-accounts");

    public String encode(String password) {
        if (!validLength(password)) throw new IllegalArgumentException("Password exceeds BCrypt's 72-byte limit");
        return bcrypt.encode(password);
    }

    public boolean matches(AuthAccount account, String password) {
        if (account == null || !validLength(password) || "GOOGLE".equals(account.authProvider())) {
            bcrypt.matches("dummy", dummy); return false;
        }
        return switch (account.passwordAlgorithm()) {
            case "bcrypt" -> bcrypt.matches(password, account.passwordHash());
            case "argon2id" -> argon2.matches(password, account.passwordHash());
            default -> { bcrypt.matches("dummy", dummy); yield false; } // Legacy SHA-256 requires email recovery.
        };
    }

    public static boolean validLength(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
