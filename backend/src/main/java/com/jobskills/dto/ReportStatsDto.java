package com.jobskills.dto;

import lombok.Data;

/** Statistics for the report management system. */
@Data
public class ReportStatsDto {
    private long openReports;
    private long underReviewReports;
    private long resolvedReports;
    private long dismissedReports;
    private long fraudReports;
    private long misinformationReports;
    private long duplicateReports;
}