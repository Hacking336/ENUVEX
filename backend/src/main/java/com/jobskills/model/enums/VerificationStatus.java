package com.jobskills.model.enums;

/**
 * Verification status for employer accounts.
 * Only administrators can change these statuses.
 */
public enum VerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED,
    NEEDS_INFORMATION;

    /** Human-readable label for display. */
    public String label() {
        return name().toLowerCase().replace('_', ' ');
    }

    /** True if the employer is verified or further review is not needed. */
    public boolean isVerified() {
        return this == VERIFIED;
    }

    /** True if the employer needs to provide additional information. */
    public boolean needsInformation() {
        return this == NEEDS_INFORMATION;
    }

    /** True if the employer is not yet verified. */
    public boolean isPendingOrUnverified() {
        return this == PENDING || this == UNVERIFIED;
    }
}