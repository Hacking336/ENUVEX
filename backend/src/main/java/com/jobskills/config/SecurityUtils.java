package com.jobskills.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

/**
 * Reads the caller's identity from the security context.
 *
 * <p>This application authenticates with {@link CustomUserDetails} (see
 * {@link JwtAuthenticationFilter}), so the principal is not Spring's own
 * {@code User}. Supporting every shape that can appear here keeps audit rows and
 * authorization checks correct no matter which filter populated the context; a
 * missing principal returns {@code null} rather than a guessed id so callers can
 * refuse the request.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Object principal = principal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        if (principal instanceof User user) {
            String username = user.getUsername();
            return isNumeric(username) ? Long.valueOf(username) : null;
        }
        if (principal instanceof String value && isNumeric(value)) {
            return Long.valueOf(value);
        }
        return null;
    }

    public static String getCurrentUsername() {
        Object principal = principal();
        if (principal instanceof CustomUserDetails details) {
            return details.getEmail();
        }
        if (principal instanceof User user) {
            return user.getUsername();
        }
        return principal instanceof String value ? value : null;
    }

    private static Object principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }

    private static boolean isNumeric(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }
}
