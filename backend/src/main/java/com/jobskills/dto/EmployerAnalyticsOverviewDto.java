package com.jobskills.dto;

/** Overview cards for the employer analytics dashboard. */
public class EmployerAnalyticsOverviewDto {

    private long totalJobPosts;
    private long activeJobPosts;
    private long totalApplicants;
    private long newApplicants;
    private long applicantsUnderReview;
    private long shortlistedApplicants;
    private long interviewedApplicants;
    private long acceptedApplicants;
    private long rejectedApplicants;
    private long upcomingInterviews;

    // Getters and setters
    public long getTotalJobPosts() { return totalJobPosts; }
    public void setTotalJobPosts(long totalJobPosts) { this.totalJobPosts = totalJobPosts; }

    public long getActiveJobPosts() { return activeJobPosts; }
    public void setActiveJobPosts(long activeJobPosts) { this.activeJobPosts = activeJobPosts; }

    public long getTotalApplicants() { return totalApplicants; }
    public void setTotalApplicants(long totalApplicants) { this.totalApplicants = totalApplicants; }

    public long getNewApplicants() { return newApplicants; }
    public void setNewApplicants(long newApplicants) { this.newApplicants = newApplicants; }

    public long getApplicantsUnderReview() { return applicantsUnderReview; }
    public void setApplicantsUnderReview(long applicantsUnderReview) { this.applicantsUnderReview = applicantsUnderReview; }

    public long getShortlistedApplicants() { return shortlistedApplicants; }
    public void setShortlistedApplicants(long shortlistedApplicants) { this.shortlistedApplicants = shortlistedApplicants; }

    public long getInterviewedApplicants() { return interviewedApplicants; }
    public void setInterviewedApplicants(long interviewedApplicants) { this.interviewedApplicants = interviewedApplicants; }

    public long getAcceptedApplicants() { return acceptedApplicants; }
    public void setAcceptedApplicants(long acceptedApplicants) { this.acceptedApplicants = acceptedApplicants; }

    public long getRejectedApplicants() { return rejectedApplicants; }
    public void setRejectedApplicants(long rejectedApplicants) { this.rejectedApplicants = rejectedApplicants; }

    public long getUpcomingInterviews() { return upcomingInterviews; }
    public void setUpcomingInterviews(long upcomingInterviews) { this.upcomingInterviews = upcomingInterviews; }
}