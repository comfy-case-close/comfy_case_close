package com.fnbx.identity.utils;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Supplier;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;

/** Human-readable codes with 60 random bits. Database constraints decide uniqueness. */
public final class BusinessCodeUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private BusinessCodeUtils() {}

    public static String generate(String name) {
        String prefix = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (prefix.isEmpty()) prefix = "BIZ";
        prefix = prefix.substring(0, Math.min(12, prefix.length())).replaceAll("-$", "");
        StringBuilder code = new StringBuilder(prefix).append('-');
        for (int i = 0; i < 12; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(32)));
        return code.toString();
    }

    public static boolean violates(DuplicateKeyException failure, String... constraints) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException pg && pg.getServerErrorMessage() != null) {
                String actual = pg.getServerErrorMessage().getConstraint();
                for (String constraint : constraints) if (constraint.equals(actual)) return true;
            }
        }
        return false;
    }

    /** operation must open a fresh transaction on each attempt (Postgres aborts failed transactions). */
    public static <T> T retry(Supplier<T> operation, String... constraints) {
        for (int attempt = 0; ; attempt++) {
            try { return operation.get(); }
            catch (DuplicateKeyException collision) {
                if (attempt >= 4 || !violates(collision, constraints)) throw collision;
            }
        }
    }
}
