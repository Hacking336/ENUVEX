package com.jobskills.ratelimit;

/**
 * Storage behind {@link RateLimiter}. The production implementation is a
 * PostgreSQL table, so a counter survives a restart and is shared by every
 * instance of the application; the tests use an in-memory map.
 */
public interface RateLimitStore {

    /**
     * Counts one hit for {@code bucketKey} in the fixed window starting at
     * {@code windowStartSeconds}, resetting the counter when the stored window
     * belongs to an earlier period.
     *
     * @return the number of hits recorded in that window, including this one
     */
    long increment(String bucketKey, long windowStartSeconds);
}
