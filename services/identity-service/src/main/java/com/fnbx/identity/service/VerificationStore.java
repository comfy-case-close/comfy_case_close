package com.fnbx.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.fnbx.identity.exception.AuthExceptions;
import com.fnbx.identity.utils.enums.OtpPurpose;
import com.fnbx.identity.utils.enums.Verdict;

/**
 * Holds the two short-lived secrets behind every "verify your email first" flow: the OTP that gets
 * mailed out, and the token handed back once that OTP checks out. Both are keyed by
 * {@link OtpPurpose} + email, live minutes, and are single-use — there is no history worth keeping,
 * so a {@link ConcurrentHashMap} replaces the database round-trips entirely.
 *
 * <p>Two consequences of keeping this in memory, both deliberate:
 * <ul>
 *   <li>A restart forgets everything — the user simply asks for a new code.</li>
 *   <li>State is per-instance — running more than one instance needs a shared store (Redis), and
 *       this class is the only place that would have to change.</li>
 * </ul>
 *
 * <p>Secrets are stored as SHA-256 hashes and compared in constant time, so a heap dump never hands
 * out a usable code. Expired entries are swept on every write, which keeps both maps bounded
 * without a scheduled job.
 */
@Service
public class VerificationStore {

    /** How long a mailed OTP stays valid. */
    public static final Duration OTP_TTL = Duration.ofMinutes(10);

    /** How long the token returned by OTP verification stays valid. */
    public static final Duration VERIFIED_TOKEN_TTL = Duration.ofMinutes(15);

    /** Minimum wait between two OTP requests for the same purpose + email. */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    /** Wrong guesses allowed before the code is burned — six digits is otherwise brute-forceable. */
    private static final int MAX_OTP_ATTEMPTS = 5;

    /** Exclusive upper bound of a 6-digit code: 000000..999999. */
    private static final int OTP_BOUND = 1_000_000;

    private static final int TOKEN_BYTES = 32;

    private final Map<String, Otp> otps = new ConcurrentHashMap<>();
    private final Map<String, VerifiedToken> tokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    public VerificationStore() {
        this(Clock.systemUTC());
    }

    /** Package-private: lets tests drive expiry and cooldown without sleeping. */
    VerificationStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * Generates and stores a fresh OTP, returning the plain code so the caller can mail it. Any
     * previous code for the same purpose + email is replaced.
     *
//     * @throws com.fnbx.identity.exception.AppException if a code was already requested moments ago
     */
    public String issueOtp(OtpPurpose purpose, String email) {
        purgeExpired();
        Instant now = clock.instant();
        // compute runs under the key's lock, so the cooldown check and the replacement cannot be
        // split: two clicks landing together produce one code, not two.
        AtomicReference<String> issued = new AtomicReference<>();
        otps.compute(key(purpose, email), (key, previous) -> {
            if (previous != null && previous.issuedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
                return previous; // still cooling down — leave the existing code alone
            }
            String otp = randomOtp();
            issued.set(otp);
            return new Otp(hash(otp), now, now.plus(OTP_TTL), 0);
        });
        if (issued.get() == null) {
            throw AuthExceptions.otpRequestTooSoon();
        }
        return issued.get();
    }

    /** Forgets a code that was never delivered, so the user can retry without waiting out the cooldown. */
    public void discardOtp(OtpPurpose purpose, String email) {
        otps.remove(key(purpose, email));
    }

    /**
     * Checks a submitted OTP and consumes it — a code never verifies twice.
     *
//     * @throws com.fnbx.identity.exception.AppException if the code is unknown, wrong, expired, or exhausted
     */
    public void consumeOtp(OtpPurpose purpose, String email, String submittedOtp) {
        Instant now = clock.instant();
        // Read, verify and increment happen as one atomic remap under the key's lock. Done as a
        // get followed by a put, a burst of parallel wrong guesses would all read the same attempt
        // count and collectively spend a single attempt — turning a five-guess limit into as many
        // guesses as the attacker can issue in parallel.
        //
        // The verdict is carried out of the lambda rather than thrown from inside it: an exception
        // thrown by the remapping function leaves the map unchanged, which would discard the very
        // increment being recorded.
        AtomicReference<Verdict> verdict = new AtomicReference<>(Verdict.INVALID);
        otps.compute(key(purpose, email), (key, stored) -> {
            if (stored == null) {
                verdict.set(Verdict.INVALID); // never reveal whether a code was ever issued
                return null;
            }
            if (stored.hasExpiredAt(now)) {
                verdict.set(Verdict.EXPIRED);
                return null;
            }
            if (matches(stored.codeHash(), submittedOtp)) {
                verdict.set(Verdict.ACCEPTED);
                return null; // single use
            }
            int attempts = stored.attempts() + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                verdict.set(Verdict.EXHAUSTED);
                return null; // burn the code — brute force stops here
            }
            verdict.set(Verdict.INVALID);
            return stored.withAttempts(attempts);
        });
        switch (verdict.get()) {
            case ACCEPTED -> {
                /* the only way out without an exception */
            }
            case EXPIRED -> throw AuthExceptions.otpExpired();
            case EXHAUSTED -> throw AuthExceptions.tooManyOtpAttempts();
            case INVALID -> throw AuthExceptions.invalidOtp();
        }
    }

    /**
     * Issues the proof that an address just passed OTP verification. The caller returns the plain
     * token to the client, which presents it when completing signup / password reset.
     */
    public String issueToken(OtpPurpose purpose, String email) {
        purgeExpired();
        String token = randomToken();
        tokens.put(
                key(purpose, email),
                new VerifiedToken(hash(token), clock.instant().plus(VERIFIED_TOKEN_TTL)));
        return token;
    }

    /**
     * Consumes the token issued by {@link #issueToken} — valid exactly once. Returns {@code false}
     * rather than throwing, so each flow can report its own error code.
     */
    public boolean consumeToken(OtpPurpose purpose, String email, String submittedToken) {
        String key = key(purpose, email);
        VerifiedToken stored = tokens.get(key);
        if (stored == null || stored.hasExpiredAt(clock.instant()) || !matches(stored.tokenHash(), submittedToken)) {
            return false; // a wrong guess must not invalidate the legitimate token
        }
        return tokens.remove(key, stored); // compare-and-remove: only one concurrent consumer wins
    }

    // ---- entries ----

    /** A mailed one-time code: hashed, time-boxed, and attempt-limited. */
    private record Otp(String codeHash, Instant issuedAt, Instant expiresAt, int attempts) {

        Otp withAttempts(int attempts) {
            return new Otp(codeHash, issuedAt, expiresAt, attempts);
        }

        boolean hasExpiredAt(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }

    /** Proof of a passed OTP check, exchanged for the action the OTP was guarding. */
    private record VerifiedToken(String tokenHash, Instant expiresAt) {

        boolean hasExpiredAt(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }

    // ---- helpers ----

    /** Keeps both maps bounded without a scheduled job — OTP volume makes the scan negligible. */
    private void purgeExpired() {
        Instant now = clock.instant();
        otps.values().removeIf(otp -> otp.hasExpiredAt(now));
        tokens.values().removeIf(token -> token.hasExpiredAt(now));
    }

    private static String key(OtpPurpose purpose, String email) {
        return purpose.name() + ':' + email;
    }

    private String randomOtp() {
        return "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean matches(String storedHash, String submitted) {
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8), hash(submitted).getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
