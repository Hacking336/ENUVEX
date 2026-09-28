package com.jobskills.dto;

import lombok.Data;

@Data
public class DashboardStatsDto {
    private long totalJobSeekers;
    private long totalEmployers;
    private long activeJobPosts;
    private long totalApplications;
    private long hiredApplicants;
}