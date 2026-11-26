package com.jobskills.model.enums;

/**
 * Status tracking for reports.
 */
public enum ReportStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED;

    /** Human-readable label. */
    public String label() {
        return name().toLowerCase().replace('_', ' ');
    }
}