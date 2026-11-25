package com.jobskills.dto;

import java.time.LocalDateTime;

/** Performance table row for each job posting. */
public class EmployerJobPerformanceDto {

    private Long id;
    private String jobTitle;
    private String municipality;
    private String barangay;
    private String employmentType;
    private LocalDateTime datePosted;
    private String status;
    private long totalApplicants;
    private long newApplicants;
    private long shortlisted;
    private long interviewed;
    private long accepted;
    private long rejected;
    private double averageMatchScore;
    private String actions;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }

    public String getBarangay() { return barangay; }
    public void setBarangay(String barangay) { this.barangay = barangay; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public LocalDateTime getDatePosted() { return datePosted; }
    public void setDatePosted(LocalDateTime datePosted) { this.datePosted = datePosted; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTotalApplicants() { return totalApplicants; }
    public void setTotalApplicants(long totalApplicants) { this.totalApplicants = totalApplicants; }

    public long getNewApplicants() { return newApplicants; }
    public void setNewApplicants(long newApplicants) { this.newApplicants = newApplicants; }

    public long getShortlisted() { return shortlisted; }
    public void setShortlisted(long shortlisted) { this.shortlisted = shortlisted; }

    public long getInterviewed() { return interviewed; }
    public void setInterviewed(long interviewed) { this.interviewed = interviewed; }

    public long getAccepted() { return accepted; }
    public void setAccepted(long accepted) { this.accepted = accepted; }

    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }

    public double getAverageMatchScore() { return averageMatchScore; }
    public void setAverageMatchScore(double averageMatchScore) { this.averageMatchScore = averageMatchScore; }

    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }
}