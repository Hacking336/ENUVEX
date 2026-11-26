package com.jobskills.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts hits in PostgreSQL with a single upsert, so two application instances
 * (or two requests arriving at the same moment) cannot both read a stale count.
 *
 * <p>The counter is a fixed window: the row remembers the window it belongs to
 * and starts again at one when a new window begins.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcRateLimitStore implements RateLimitStore {

    /** One window older than this is not needed by any limit and is deleted. */
    private static final long CLEANUP_AFTER_HOURS = 24;
    private static final long CLEANUP_INTERVAL_MS = 10 * 60 * 1000L;

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong lastCleanup = new AtomicLong(0);

    @Override
    public long increment(String bucketKey, long windowStartSeconds) {
        String sql = """
            INSERT INTO rate_limit_counters AS c (bucket_key, window_start, hits, updated_at)
            VALUES (?, ?, 1, CURRENT_TIMESTAMP)
            ON CONFLICT (bucket_key) DO UPDATE SET
                hits = CASE WHEN c.window_start = EXCLUDED.window_start THEN c.hits + 1 ELSE 1 END,
                window_start = CASE WHEN c.window_start = EXCLUDED.window_start
                                    THEN c.window_start ELSE EXCLUDED.window_start END,
                updated_at = CURRENT_TIMESTAMP
            RETURNING hits
            """;
        Long hits = jdbcTemplate.queryForObject(sql, Long.class, bucketKey, windowStartSeconds);
        cleanupOccasionally();
        return hits == null ? 1L : hits;
    }

    /**
     * Removes expired counters at most once every ten minutes, so the table
     * stays small without a scheduler thread or an extra dependency.
     */
    private void cleanupOccasionally() {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MS || !lastCleanup.compareAndSet(last, now)) {
            return;
        }
        try {
            int removed = jdbcTemplate.update(
                "DELETE FROM rate_limit_counters WHERE updated_at < CURRENT_TIMESTAMP - (? || ' hours')::interval",
                CLEANUP_AFTER_HOURS);
            if (removed > 0) {
                log.debug("Rate-limit cleanup removed {} expired counters", removed);
            }
        } catch (RuntimeException e) {
            log.debug("Rate-limit cleanup skipped: {}", e.getMessage());
        }
    }
}
