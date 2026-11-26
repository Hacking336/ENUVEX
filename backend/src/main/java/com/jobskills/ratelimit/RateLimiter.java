package com.jobskills.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Fixed-window rate limiting for the four write endpoints an unauthenticated
 * attacker can hammer: sign-in, registration, report submission and job
 * application.
 *
 * <p>The limits are configuration, not code ({@code jsm.rate-limit.*}), and are
 * deliberately generous: a person who mistypes a password a few times is never
 * locked out, while a script trying hundreds of combinations is stopped. The
 * limiter fails <em>open</em> if the counter table cannot be read, so a database
 * hiccup can never prevent a legitimate user from signing in.
 */
@Slf4j
@Component
public class RateLimiter {

    private final RateLimitStore store;

    private final boolean enabled;
    private final int loginLimit;
    private final int loginWindowSeconds;
    private final int registrationLimit;
    private final int registrationWindowSeconds;
    private final int reportLimit;
    private final int reportWindowSeconds;
    private final int applicationLimit;
    private final int applicationWindowSeconds;

    public RateLimiter(RateLimitStore store,
                       @Value("${jsm.rate-limit.enabled:true}") boolean enabled,
                       @Value("${jsm.rate-limit.login-limit:10}") int loginLimit,
                       @Value("${jsm.rate-limit.login-window-seconds:60}") int loginWindowSeconds,
                       @Value("${jsm.rate-limit.registration-limit:5}") int registrationLimit,
                       @Value("${jsm.rate-limit.registration-window-seconds:3600}") int registrationWindowSeconds,
                       @Value("${jsm.rate-limit.report-limit:10}") int reportLimit,
                       @Value("${jsm.rate-limit.report-window-seconds:3600}") int reportWindowSeconds,
                       @Value("${jsm.rate-limit.application-limit:30}") int applicationLimit,
                       @Value("${jsm.rate-limit.application-window-seconds:3600}") int applicationWindowSeconds) {
        this.store = store;
        this.enabled = enabled;
        this.loginLimit = loginLimit;
        this.loginWindowSeconds = loginWindowSeconds;
        this.registrationLimit = registrationLimit;
        this.registrationWindowSeconds = registrationWindowSeconds;
        this.reportLimit = reportLimit;
        this.reportWindowSeconds = reportWindowSeconds;
        this.applicationLimit = applicationLimit;
        this.applicationWindowSeconds = applicationWindowSeconds;
    }

    /** The actions the filter protects. Each has its own counter and window. */
    public enum Action {
        LOGIN, REGISTRATION, REPORT, APPLICATION
    }

    public record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
        public static Decision allow(int limit, int remaining) {
            return new Decision(true, limit, remaining, 0);
        }
    }

    public Decision check(Action action, String clientKey) {
        int limit = limitFor(action);
        int windowSeconds = windowFor(action);
        if (!enabled || limit <= 0) {
            return Decision.allow(Math.max(limit, 0), Math.max(limit, 0));
        }

        long now = Instant.now().getEpochSecond();
        long windowStart = now - (now % windowSeconds);
        String bucketKey = "jsm:rate:" + action.name().toLowerCase() + ":" + sanitize(clientKey);

        try {
            long hits = store.increment(bucketKey, windowStart);
            long retryAfter = Math.max(1, windowStart + windowSeconds - now);
            if (hits > limit) {
                return new Decision(false, limit, 0, retryAfter);
            }
            return Decision.allow(limit, (int) Math.max(0, limit - hits));
        } catch (DataAccessException e) {
            log.warn("Rate limiting unavailable for {} ({}); allowing the request", action, e.getMessage());
            return Decision.allow(limit, limit);
        }
    }

    private int limitFor(Action action) {
        return switch (action) {
            case LOGIN -> loginLimit;
            case REGISTRATION -> registrationLimit;
            case REPORT -> reportLimit;
            case APPLICATION -> applicationLimit;
        };
    }

    private int windowFor(Action action) {
        return switch (action) {
            case LOGIN -> loginWindowSeconds;
            case REGISTRATION -> registrationWindowSeconds;
            case REPORT -> reportWindowSeconds;
            case APPLICATION -> applicationWindowSeconds;
        };
    }

    /** Keeps a client-supplied value inside the column width. */
    private String sanitize(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return "unknown";
        }
        String trimmed = clientKey.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }
}
