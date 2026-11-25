package com.jobskills.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Complete exportable analytics report for an employer. */
public class EmployerAnalyticsExportDto {

    private EmployerAnalyticsOverviewDto overview;
    private EmployerApplicantFunnelDto funnel;
    private EmployerMatchingInsightsDto matching;
    private EmployerGeographicInsightsDto geographic;
    private List<EmployerInsightDto> insights;
    private List<EmployerJobPerformanceDto> jobPerformances;
    private String dateRange;
    private LocalDateTime generatedAt;

    // Getters and setters
    public EmployerAnalyticsOverviewDto getOverview() { return overview; }
    public void setOverview(EmployerAnalyticsOverviewDto overview) { this.overview = overview; }

    public EmployerApplicantFunnelDto getFunnel() { return funnel; }
    public void setFunnel(EmployerApplicantFunnelDto funnel) { this.funnel = funnel; }

    public EmployerMatchingInsightsDto getMatching() { return matching; }
    public void setMatching(EmployerMatchingInsightsDto matching) { this.matching = matching; }

    public EmployerGeographicInsightsDto getGeographic() { return geographic; }
    public void setGeographic(EmployerGeographicInsightsDto geographic) { this.geographic = geographic; }

    public List<EmployerInsightDto> getInsights() { return insights; }
    public void setInsights(List<EmployerInsightDto> insights) { this.insights = insights; }

    public List<EmployerJobPerformanceDto> getJobPerformances() { return jobPerformances; }
    public void setJobPerformances(List<EmployerJobPerformanceDto> jobPerformances) { this.jobPerformances = jobPerformances; }

    public String getDateRange() { return dateRange; }
    public void setDateRange(String dateRange) { this.dateRange = dateRange; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}