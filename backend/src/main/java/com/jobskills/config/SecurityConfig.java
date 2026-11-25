package com.jobskills.config;

import com.jobskills.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // https://localhost and capacitor://localhost are the origins a Capacitor
    // WebView reports on Android and iOS; without them the packaged apps cannot
    // call the API. They are the app's own local shell, not a website.
    private static final List<String> FIXED_ORIGINS = List.of(
        "http://localhost:8080",
        "http://127.0.0.1:8080",
        "https://enuvex.onrender.com",
        "https://jobskillmatching.com",
        "https://www.jobskillmatching.com",
        "https://localhost",
        "capacitor://localhost",
        "http://localhost");

    /**
     * Resolved per request instead of from a fixed list, because browsers send
     * an Origin header on every POST/PUT/DELETE - even same-origin ones. A host
     * that was not written into the list (the custom domain, a preview host, a
     * new deployment URL) therefore got 403 "Invalid CORS request" on login,
     * registration and posting a job, while GETs kept working.
     *
     * The origin the browser reports is accepted when it is the same host the
     * browser used to reach us, so a deployment works on whatever hostname it is
     * served from without opening the API to unrelated sites.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration configuration = new CorsConfiguration();
            List<String> origins = new ArrayList<>(FIXED_ORIGINS);
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin != null && isSameHost(origin, request)) {
                origins.add(origin);
            }
            configuration.setAllowedOrigins(origins.stream().distinct().toList());
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setAllowCredentials(true);
            return configuration;
        };
    }

    /** True when the request behind this Origin reached us on the same host name. */
    private boolean isSameHost(String origin, HttpServletRequest request) {
        String host = firstValue(request.getHeader("X-Forwarded-Host"));
        if (host == null) {
            host = firstValue(request.getHeader(HttpHeaders.HOST));
        }
        if (host == null) {
            return false;
        }
        try {
            String originHost = java.net.URI.create(origin).getHost();
            String requestHost = java.net.URI.create("http://" + host).getHost();
            return originHost != null && originHost.equalsIgnoreCase(requestHost);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String firstValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String first = headerValue.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Missing or invalid token answers 401, not 403, so the frontend can
            // tell "log in again" apart from "not allowed".
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                    org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // Static pages are only the shell. The API calls inside them are
                // still protected, so the pages must be loadable by the browser.
                .requestMatchers("/", "/index.html", "/*.html").permitAll()
                .requestMatchers("/assets/**", "/uploads/**").permitAll()
                .requestMatchers("/job-seeker/**", "/employer/**", "/admin/**").permitAll()
                // Public lookups used by the registration forms.
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/municipalities", "/api/categories", "/api/business-types").permitAll()
                // PSGC reference data and boundaries are non-personal background
                // layers; map pages need them before a seeker signs in.
                .requestMatchers(HttpMethod.GET, "/api/geo/**").permitAll()
                // Read-only job browsing is public: JobController only exposes
                // GETs here and they return active posts. Without this rule the
                // job listing and job detail pages answered 401 to anyone not
                // signed in, even though the controllers document them as public.
                .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            );

        http.addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
            UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
