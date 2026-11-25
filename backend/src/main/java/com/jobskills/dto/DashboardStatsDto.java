package com.jobskills.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DashboardStatsDto {
    private long totalJobSeekers;
    private long totalEmployers;
    private long activeJobPosts;
    private long totalApplications;
    private long hiredApplicants;
    /** Admin activity summary: applications per lifecycle status. */
    private Map<String, Long> applicationsByStatus = new LinkedHashMap<>();
}