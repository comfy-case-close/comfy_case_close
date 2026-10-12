package com.fnbx.cashclose.scheduler;

import com.fnbx.cashclose.repository.CashCloseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fnb.cashclose.daily-creation.enabled", matchIfMissing = true)
public class DailyCashCloseJob {
    private static final Logger log = LoggerFactory.getLogger(DailyCashCloseJob.class);
    private final CashCloseRepository closes;

    /** Each business may enter a new day at a different UTC time; also catches up on restart. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${fnb.cashclose.daily-creation.cron:0 * * * * *}")
    @Transactional
    public void createTodayCloses() {
        int created = closes.createDailyCloses();
        if (created > 0) log.info("Created {} daily cash closes", created);
    }
}
