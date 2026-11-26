package com.jobskills.model.enums;

/**
 * Moderation status for job postings.
 * Separate from the active/inactive lifecycle status.
 * Only administrators can change these statuses.
 */
public enum ModerationStatus {
    PENDING_REVIEW,
    APPROVED,
    CHANGES_REQUESTED,
    REJECTED;

    /** Human-readable label. */
    public String label() {
        return name().toLowerCase().replace('_', ' ');
    }

    /** True if the posting is visible to job seekers. */
    public boolean isVisible() {
        return this == APPROVED || this == PENDING_REVIEW;
    }

    /** True if the posting requires employer action. */
    public boolean needsChanges() {
        return this == CHANGES_REQUESTED;
    }

    /** True if the posting is not visible. */
    public boolean isRejected() {
        return this == REJECTED;
    }
}