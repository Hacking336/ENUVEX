package com.jobskills.model.enums;

import java.util.Locale;

/**
 * The application lifecycle.
 *
 * SUBMITTED and REVIEWING were named APPLIED and UNDER_REVIEW in the first
 * version. V6 renames the stored values and {@link #from(String)} still maps the
 * old names, so a stale client or an un-migrated row cannot break a request.
 */
public enum ApplicationStatus {
    SUBMITTED,
    REVIEWING,
    SHORTLISTED,
    INTERVIEW,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    /** Human label used in notifications, timelines and the API. */
    public String label() {
        String name = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** True for the states an employer may act on. */
    public boolean isOpen() {
        return this == SUBMITTED || this == REVIEWING || this == SHORTLISTED || this == INTERVIEW;
    }

    /** Parses a status, accepting the pre-V6 names. */
    public static ApplicationStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("APPLIED".equals(value)) return SUBMITTED;
        if ("UNDER_REVIEW".equals(value)) return REVIEWING;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown application status: " + raw
                + ". Use " + "SUBMITTED, REVIEWING, SHORTLISTED, INTERVIEW, ACCEPTED, REJECTED or WITHDRAWN.");
        }
    }
}
