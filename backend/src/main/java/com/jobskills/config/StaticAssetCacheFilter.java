package com.jobskills.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Spring Security stamps every response with "no-store", so the browser
 * re-downloaded the whole design system (CSS, Font Awesome, the shared shell
 * script) on every single page. Those files only change when the app is
 * redeployed, so they may be reused briefly; the HTML shell keeps revalidating
 * so a deploy is still picked up.
 *
 * Runs inside the security filter chain, after its headers were written, so the
 * value set here is the one that reaches the browser. API responses are left
 * untouched.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StaticAssetCacheFilter extends OncePerRequestFilter {

    private static final String ASSET_CACHE = "public, max-age=600";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().toLowerCase(Locale.ROOT);

        if (!path.startsWith("/api/")) {
            if (isAsset(path)) {
                response.setHeader("Cache-Control", ASSET_CACHE);
            } else if (path.endsWith(".html") || path.endsWith("/")) {
                // Revalidate, but a browser may keep the parsed page and get a 304.
                response.setHeader("Cache-Control", "no-cache");
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAsset(String path) {
        return path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png")
            || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif")
            || path.endsWith(".svg") || path.endsWith(".ico") || path.endsWith(".webp")
            || path.endsWith(".woff") || path.endsWith(".woff2");
    }
}
