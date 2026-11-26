package com.jobskills;

import com.jobskills.ratelimit.RateLimitFilter;
import com.jobskills.ratelimit.RateLimitStore;
import com.jobskills.ratelimit.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Phase 8 rate limiter: the counting rules, the failure
 * behaviour, and the exact set of endpoints it protects. The PostgreSQL store is
 * replaced by a map, so the tests need no database.
 */
class RateLimiterTest {

    private static final class MemoryStore implements RateLimitStore {
        final Map<String, Long> hits = new HashMap<>();
        final Map<String, Long> windows = new HashMap<>();
        boolean broken;

        @Override
        public long increment(String bucketKey, long windowStartSeconds) {
            if (broken) {
                throw new DataAccessResourceFailureException("database unavailable");
            }
            Long currentWindow = windows.get(bucketKey);
            long count = (currentWindow != null && currentWindow == windowStartSeconds)
                ? hits.get(bucketKey) + 1 : 1;
            windows.put(bucketKey, windowStartSeconds);
            hits.put(bucketKey, count);
            return count;
        }
    }

    private RateLimiter limiter(MemoryStore store, boolean enabled) {
        return new RateLimiter(store, enabled, 2, 3600, 2, 3600, 2, 3600, 2, 3600);
    }

    @Test
    @DisplayName("Ten sign-in attempts are allowed, the eleventh is refused with a retry hint")
    void allowsUpToTheLimitThenRefuses() {
        MemoryStore store = new MemoryStore();
        RateLimiter limiter = new RateLimiter(store, true, 10, 60, 5, 3600, 10, 3600, 30, 3600);

        for (int attempt = 1; attempt <= 10; attempt++) {
            RateLimiter.Decision decision = limiter.check(RateLimiter.Action.LOGIN, "203.0.113.7");
            assertThat(decision.allowed()).as("attempt %s", attempt).isTrue();
            assertThat(decision.remaining()).isEqualTo(10 - attempt);
        }

        RateLimiter.Decision blocked = limiter.check(RateLimiter.Action.LOGIN, "203.0.113.7");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isZero();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("Each client address and each action has its own counter")
    void countersArePerClientAndPerAction() {
        MemoryStore store = new MemoryStore();
        RateLimiter limiter = limiter(store, true);

        assertThat(limiter.check(RateLimiter.Action.LOGIN, "198.51.100.1").allowed()).isTrue();
        assertThat(limiter.check(RateLimiter.Action.LOGIN, "198.51.100.1").allowed()).isTrue();
        assertThat(limiter.check(RateLimiter.Action.LOGIN, "198.51.100.1").allowed()).isFalse();

        // A different address is unaffected.
        assertThat(limiter.check(RateLimiter.Action.LOGIN, "198.51.100.2").allowed()).isTrue();
        // The same address is unaffected on a different action.
        assertThat(limiter.check(RateLimiter.Action.REGISTRATION, "198.51.100.1").allowed()).isTrue();
    }

    @Test
    @DisplayName("A new window starts the count again")
    void windowResetAllowsAgain() {
        MemoryStore store = new MemoryStore();
        RateLimiter limiter = limiter(store, true);

        limiter.check(RateLimiter.Action.LOGIN, "192.0.2.9");
        limiter.check(RateLimiter.Action.LOGIN, "192.0.2.9");
        assertThat(limiter.check(RateLimiter.Action.LOGIN, "192.0.2.9").allowed()).isFalse();

        // Simulate the stored window moving into the past, which is what happens
        // when the clock passes the end of the fixed window.
        String key = "jsm:rate:login:192.0.2.9";
        store.windows.put(key, store.windows.get(key) - 3600);

        assertThat(limiter.check(RateLimiter.Action.LOGIN, "192.0.2.9").allowed()).isTrue();
    }

    @Test
    @DisplayName("The limiter can be switched off without a code change")
    void disabledLimiterAllowsEverything() {
        MemoryStore store = new MemoryStore();
        RateLimiter limiter = limiter(store, false);

        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(limiter.check(RateLimiter.Action.LOGIN, "192.0.2.10").allowed()).isTrue();
        }
        assertThat(store.hits).isEmpty();
    }

    @Test
    @DisplayName("A database failure fails open, so nobody is locked out by an outage")
    void storeFailureAllowsTheRequest() {
        MemoryStore store = new MemoryStore();
        store.broken = true;
        RateLimiter limiter = limiter(store, true);

        RateLimiter.Decision decision = limiter.check(RateLimiter.Action.LOGIN, "192.0.2.11");
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    @DisplayName("Only the four abuse-prone POST routes are limited")
    void filterProtectsOnlyTheIntendedRoutes() {
        assertThat(actionFor("POST", "/api/auth/login")).isEqualTo(RateLimiter.Action.LOGIN);
        assertThat(actionFor("POST", "/api/auth/register")).isEqualTo(RateLimiter.Action.REGISTRATION);
        assertThat(actionFor("POST", "/api/moderation/reports")).isEqualTo(RateLimiter.Action.REPORT);
        assertThat(actionFor("POST", "/api/job-seekers/apply")).isEqualTo(RateLimiter.Action.APPLICATION);

        // Reads and unrelated writes are not limited.
        assertThat(actionFor("GET", "/api/auth/login")).isNull();
        assertThat(actionFor("GET", "/api/jobs")).isNull();
        assertThat(actionFor("POST", "/api/employers/jobs")).isNull();
        assertThat(actionFor("PUT", "/api/job-seekers/profile")).isNull();
        assertThat(actionFor("POST", "/api/notifications/read-all")).isNull();

        // A trailing slash still matches, so the limit cannot be bypassed by adding one.
        assertThat(actionFor("POST", "/api/auth/login/")).isEqualTo(RateLimiter.Action.LOGIN);
    }

    @Test
    @DisplayName("The client key is the forwarded address, not the proxy")
    void clientKeyUsesForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.55, 10.0.0.1");
        assertThat(RateLimitFilter.clientKey(request)).isEqualTo("203.0.113.55");

        MockHttpServletRequest direct = new MockHttpServletRequest("POST", "/api/auth/login");
        direct.setRemoteAddr("10.0.0.2");
        assertThat(RateLimitFilter.clientKey(direct)).isEqualTo("10.0.0.2");
    }

    private RateLimiter.Action actionFor(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        return RateLimitFilter.actionFor(request);
    }
}
