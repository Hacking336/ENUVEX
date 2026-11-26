package com.jobskills.dto;

import lombok.Data;

/** Overview of moderation status across the platform. */
@Data
public class ModerationOverviewDto {
    /** Total user accounts. */
    private long totalUsers;

    /** Total employer accounts. */
    private long totalEmployers;

    /** Breakdown by verification status. */
    private long verifiedEmployers;
    private long pendingVerifications;
    private long rejectedEmployers;
    private long needsInformationEmployers;
    private long unverifiedEmployers;

    /** Job posting moderation breakdown. */
    private long totalJobPosts;
    private long approvedJobs;
    private long pendingReviewJobs;
    private long changesRequestedJobs;
    private long rejectedJobs;
    private long activeJobs;

    /** Report breakdown. */
    private long openReports;
    private long underReviewReports;
    private long resolvedReports;
    private long dismissedReports;
    private long suspiciousListings;

    /** Suspended accounts. */
    private long suspendedAccounts;
}