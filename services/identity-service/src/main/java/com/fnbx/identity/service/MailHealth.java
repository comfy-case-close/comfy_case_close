package com.fnbx.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Latches a provider-wide mail outage so callers can refuse work instead of queueing it.
 *
 * <p>Delivery runs off the request thread, so the request that trips the fault is already answered
 * by the time it fails. This is what the next request reads: a bad API key refuses everyone, and
 * sending the following user to a "check your inbox" screen for a mail that cannot arrive only
 * hides the outage from them and from you.
 *
 * <p>The latch expires on its own. Nothing here notices that the key was fixed, so after the window
 * one caller is let through to find out — the half-open step of a circuit breaker. Without it the
 * outage would outlive its cause until the next restart.
 */
@Component
public class MailHealth {

    private static final Duration OUTAGE_WINDOW = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(MailHealth.class);

    private final AtomicReference<Outage> outage = new AtomicReference<>();
    private final Clock clock;

    public MailHealth() {
        this(Clock.systemUTC());
    }

    /** Package-private: lets tests move past the window without sleeping. */
    MailHealth(Clock clock) {
        this.clock = clock;
    }

    public void recordProviderFault(String reason) {
        outage.set(new Outage(reason, clock.instant().plus(OUTAGE_WINDOW)));
        log.error("Mail provider is refusing everything; pausing OTP requests for {}: {}", OUTAGE_WINDOW, reason);
    }

    /** Any delivery getting through proves the provider is answering again. */
    public void recordSuccess() {
        if (outage.getAndSet(null) != null) {
            log.info("Mail provider recovered; OTP requests resume");
        }
    }

    /**
     * True while mail is known to be down. Clears itself once the window lapses, which is why this
     * is not a plain getter: the caller that finds the lapsed latch becomes the probe.
     */
    public boolean isDown() {
        Outage current = outage.get();
        if (current == null) {
            return false;
        }
        if (!current.until().isAfter(clock.instant())) {
            outage.compareAndSet(current, null); // half-open: let this one through
            return false;
        }
        return true;
    }

    /** For an admin or health endpoint — never for an end user. */
    public Optional<String> outageReason() {
        return Optional.ofNullable(outage.get()).map(Outage::reason);
    }

    private record Outage(String reason, Instant until) {}
}
