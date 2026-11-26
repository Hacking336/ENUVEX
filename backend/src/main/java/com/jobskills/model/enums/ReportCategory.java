package com.jobskills.model.enums;

/**
 * Categories for job and employer reports.
 */
public enum ReportCategory {
    FRAUD_OR_SCAM,
    MISLEADING_INFORMATION,
    FAKE_EMPLOYER,
    DUPLICATE_LISTING,
    WRONG_LOCATION,
    INAPPROPRIATE_CONTENT,
    OTHER;

    /** Human-readable label. */
    public String label() {
        return name().toLowerCase().replace('_', ' ');
    }
}