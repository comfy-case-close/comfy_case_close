package com.fnbx.identity.scheduler;

import java.time.Clock;
import java.time.Duration;
import com.fnbx.identity.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RevokedTokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanupJob.class);
    private final RevokedTokenRepository revoked;
    private final TransactionTemplate transactions;
    private final Clock clock;
    public RevokedTokenCleanupJob(RevokedTokenRepository revoked, TransactionTemplate transactions, Clock clock) {
        this.revoked = revoked; this.transactions = transactions; this.clock = clock;
    }

    /** Vakot's nightly cleanup and one-day margin, exceeding Nimbus's 60-second skew. */
    @Scheduled(cron = "${fnb.security.jwt.revoked-token-cleanup-cron:0 30 3 * * *}")
    public void purgeExpired() {
        Integer deleted = transactions.execute(status -> revoked.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1))));
        if (deleted != null && deleted > 0) log.info("Purged {} expired refresh-token revocations", deleted);
    }
}
