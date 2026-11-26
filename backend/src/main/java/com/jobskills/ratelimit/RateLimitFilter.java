package com.jobskills.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Applies {@link RateLimiter} to the four write endpoints that a script can
 * abuse without an account: sign-in, registration, report submission and job
 * application. Everything else passes straight through.
 *
 * <p>Not a @Component: SecurityConfig adds it to the security chain once, in
 * front of authentication, so the limit applies before any password is checked.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiter.Action action = actionFor(request);
        if (action == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision = rateLimiter.check(action, clientKey(request));
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(String.format(
                "{\"error\":\"Too many requests. Please wait %d seconds and try again.\","
                    + "\"status\":429,\"retryAfterSeconds\":%d}",
                decision.retryAfterSeconds(), decision.retryAfterSeconds()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** The protected endpoints, matched on method and exact path. */
    public static RateLimiter.Action actionFor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = normalize(request.getRequestURI(), request.getContextPath());
        if (!HttpMethod.POST.matches(method)) {
            return null;
        }
        return switch (path) {
            case "/api/auth/login" -> RateLimiter.Action.LOGIN;
            case "/api/auth/register" -> RateLimiter.Action.REGISTRATION;
            case "/api/moderation/reports" -> RateLimiter.Action.REPORT;
            case "/api/job-seekers/apply" -> RateLimiter.Action.APPLICATION;
            default -> null;
        };
    }

    private static String normalize(String uri, String contextPath) {
        if (uri == null) {
            return "";
        }
        String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
            ? uri.substring(contextPath.length())
            : uri;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * The caller's address. Behind Render (or any reverse proxy) the socket
     * address is the proxy, so X-Forwarded-For is used instead.
     *
     * <p>The <strong>last</strong> entry is taken, not the first: a client can
     * send its own X-Forwarded-For header, and the proxy appends the address it
     * actually saw to the end of that list. Reading the first entry would let an
     * attacker choose its own bucket and step around the limit entirely; the
     * last entry is the one the trusted proxy wrote.
     */
    public static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
