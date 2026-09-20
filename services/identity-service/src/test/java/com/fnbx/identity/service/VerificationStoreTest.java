package com.fnbx.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fnbx.shared.exception.AppException;
import com.fnbx.identity.utils.enums.OtpPurpose;

/**
 * Unit tests for the in-memory OTP / verification-token store. Time is driven by a mutable clock,
 * so expiry and cooldown are exercised without sleeping.
 */
class VerificationStoreTest {

    private static final String EMAIL = "shopper@vakot.com";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final VerificationStore store = new VerificationStore(clock);

    // ---- OTP ----

    @Test
    void consumeOtp_acceptsTheIssuedCode() {
        String otp = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);

        assertThat(otp).hasSize(6).containsOnlyDigits();
        assertThatCode(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, otp)).doesNotThrowAnyException();
    }

    @Test
    void consumeOtp_acceptsACodeOnlyOnce() {
        String otp = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);
        store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, otp);

        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, otp))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2404));
    }

    @Test
    void consumeOtp_rejectsWrongCode() {
        String otp = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);

        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, otherThan(otp)))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2404));
    }

    @Test
    void consumeOtp_rejectsUnknownEmail() {
        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, "ghost@vakot.com", "123456"))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2404));
    }

    @Test
    void consumeOtp_rejectsExpiredCode() {
        String otp = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);
        clock.advance(VerificationStore.OTP_TTL.plusSeconds(1));

        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, otp))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2405));
    }

    @Test
    void consumeOtp_burnsTheCodeAfterTooManyWrongGuesses() {
        String otp = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);
        String wrong = otherThan(otp);
        for (int attempt = 1; attempt < 5; attempt++) {
            assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, wrong))
                    .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode().getCode())
                            .isEqualTo(2404));
        }

        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, wrong))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode().getCode())
                        .isEqualTo(2417));
        // the correct code no longer works either — brute force stops here
        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, otp))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2404));
    }

    @Test
    void consumeOtp_chargesEveryConcurrentWrongGuessItsOwnAttempt() throws Exception {
        String otp = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);
        String wrong = otherThan(otp);
        int guesses = 5; // exactly the allowance, all fired at once

        List<Integer> outcomes = inParallel(guesses, () -> {
            try {
                store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, wrong);
                return null;
            } catch (AppException ex) {
                return ex.getErrorCode().getCode();
            }
        });

        // Read-then-write accounting would let all five share one attempt and every one of them
        // would come back INVALID_OTP, leaving the code alive for the next parallel batch.
        assertThat(outcomes).containsOnlyOnce(2417);
        assertThat(outcomes).filteredOn(code -> code == 2404).hasSize(guesses - 1);
        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, otp))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2404));
    }

    @Test
    void issueOtp_issuesOneCodeForConcurrentRequests() throws Exception {
        List<String> issued = inParallel(4, () -> {
            try {
                return store.issueOtp(OtpPurpose.SIGNUP, EMAIL);
            } catch (AppException ex) {
                return null; // lost the race, and the cooldown said so
            }
        });

        // One winner; the rest must not mint extra codes, or a double click mails two codes and
        // only the last one works.
        assertThat(issued).filteredOn(java.util.Objects::nonNull).hasSize(1);
    }

    @Test
    void issueOtp_rejectsAResendWithinTheCooldown() {
        store.issueOtp(OtpPurpose.SIGNUP, EMAIL);

        assertThatThrownBy(() -> store.issueOtp(OtpPurpose.SIGNUP, EMAIL))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode().getCode())
                        .isEqualTo(2416));
    }

    @Test
    void issueOtp_allowsAResendAfterTheCooldownAndInvalidatesTheOldCode() {
        String first = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);
        clock.advance(Duration.ofSeconds(61));

        String second = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);

        if (!second.equals(first)) assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, first))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2404));
        assertThatCode(() -> store.consumeOtp(OtpPurpose.SIGNUP, EMAIL, second)).doesNotThrowAnyException();
    }

    @Test
    void discardOtp_allowsAnImmediateRetry() {
        store.issueOtp(OtpPurpose.SIGNUP, EMAIL);
        store.discardOtp(OtpPurpose.SIGNUP, EMAIL);

        assertThatCode(() -> store.issueOtp(OtpPurpose.SIGNUP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void consumeOtp_keepsPurposesApart() {
        String signupOtp = store.issueOtp(OtpPurpose.SIGNUP, EMAIL);

        assertThatThrownBy(() -> store.consumeOtp(OtpPurpose.PASSWORD_RESET, EMAIL, signupOtp))
                .isInstanceOfSatisfying(
                        AppException.class, ex -> assertThat(ex.getErrorCode().getCode()).isEqualTo(2404));
    }

    // ---- verified token ----

    @Test
    void consumeToken_acceptsTheIssuedTokenOnce() {
        String token = store.issueToken(OtpPurpose.SIGNUP, EMAIL);

        assertThat(store.consumeToken(OtpPurpose.SIGNUP, EMAIL, token)).isTrue();
        assertThat(store.consumeToken(OtpPurpose.SIGNUP, EMAIL, token)).isFalse();
    }

    @Test
    void consumeToken_rejectsUnknownAndExpiredTokens() {
        assertThat(store.consumeToken(OtpPurpose.SIGNUP, EMAIL, "never-issued")).isFalse();

        String token = store.issueToken(OtpPurpose.SIGNUP, EMAIL);
        clock.advance(VerificationStore.VERIFIED_TOKEN_TTL.plusSeconds(1));

        assertThat(store.consumeToken(OtpPurpose.SIGNUP, EMAIL, token)).isFalse();
    }

    @Test
    void consumeToken_survivesAWrongGuess() {
        String token = store.issueToken(OtpPurpose.SIGNUP, EMAIL);

        assertThat(store.consumeToken(OtpPurpose.SIGNUP, EMAIL, "guessed")).isFalse();
        assertThat(store.consumeToken(OtpPurpose.SIGNUP, EMAIL, token)).isTrue();
    }

    @Test
    void consumeToken_keepsPurposesApart() {
        String signupToken = store.issueToken(OtpPurpose.SIGNUP, EMAIL);

        assertThat(store.consumeToken(OtpPurpose.PASSWORD_RESET, EMAIL, signupToken))
                .isFalse();
    }

    @Test
    void consumeToken_hasExactlyOneWinnerAcrossConcurrentCalls() throws Exception {
        String token = store.issueToken(OtpPurpose.SIGNUP, EMAIL);
        assertThat(inParallel(12, () -> store.consumeToken(OtpPurpose.SIGNUP, EMAIL, token)))
                .filteredOn(Boolean::booleanValue).hasSize(1);
    }

    @Test
    void verificationIsBoundToTenantAsWellAsPurpose() {
        String proof = store.issueToken(OtpPurpose.PASSWORD_RESET, "tenant-a:" + EMAIL);
        assertThat(store.consumeToken(OtpPurpose.PASSWORD_RESET, "tenant-b:" + EMAIL, proof)).isFalse();
        assertThat(store.consumeToken(OtpPurpose.PASSWORD_RESET, "tenant-a:" + EMAIL, proof)).isTrue();
    }

    @Test
    void businessRegistrationProofExpiresIndependentlyAfterOtpVerification() {
        String otp = store.issueOtp(OtpPurpose.BUSINESS_REGISTRATION, EMAIL);
        clock.advance(VerificationStore.OTP_TTL.minusSeconds(1));
        store.consumeOtp(OtpPurpose.BUSINESS_REGISTRATION, EMAIL, otp);
        String token = store.issueToken(OtpPurpose.BUSINESS_REGISTRATION, EMAIL);
        clock.advance(VerificationStore.VERIFIED_TOKEN_TTL);
        assertThat(store.consumeToken(OtpPurpose.BUSINESS_REGISTRATION, EMAIL, token)).isFalse();
    }

    // ---- helpers ----

    /** Runs {@code task} on {@code count} threads released together, and collects what each returned. */
    private static <T> List<T> inParallel(int count, Callable<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown(); // release them at once, to maximise the overlap
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Any 6-digit code other than {@code otp} — keeps "wrong code" tests free of random collisions. */
    private static String otherThan(String otp) {
        return otp.equals("000000") ? "111111" : "000000";
    }

    /** A clock the test can move forward, so expiry and cooldown need no sleeping. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
