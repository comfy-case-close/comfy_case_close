package com.fnbx.hrm.service.confirmation;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Fixed-window request counter for the unauthenticated confirmation links, keyed by caller address and by token. */
@Component
public class PublicRequestLimiter {

    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int MAX_PER_ADDRESS = 30;
    private static final int MAX_PER_TOKEN = 10;
    private static final int MAX_TRACKED_KEYS = 50_000;

    private record Window(long startedAt, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String address, String tokenHash) {
        hit("ip:" + address, MAX_PER_ADDRESS);
        hit("token:" + tokenHash, MAX_PER_TOKEN);
    }

    private void hit(String key, int limit) {
        long now = System.nanoTime();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.clear();
        }
        Window updated = windows.merge(key, new Window(now, 1),
                (current, fresh) -> now - current.startedAt() > WINDOW_NANOS ? fresh : new Window(current.startedAt(), current.count() + 1));
        if (updated.count() > limit) {
            throw new AppException(ErrorCode.RATE_LIMITED);
        }
    }
}
