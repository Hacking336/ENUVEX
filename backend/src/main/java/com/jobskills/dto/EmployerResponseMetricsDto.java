package com.jobskills.dto;

/** Time-based application response metrics. */
public class EmployerResponseMetricsDto {

    private double timeToReviewAvg;
    private double timeToReviewMedian;
    private boolean timeToReviewAvailable;
    private String timeToReviewDesc;

    private double timeToShortlistAvg;
    private double timeToShortlistMedian;
    private boolean timeToShortlistAvailable;

    private double timeToInterviewAvg;
    private double timeToInterviewMedian;
    private boolean timeToInterviewAvailable;

    private double timeToDecisionAvg;
    private double timeToDecisionMedian;
    private boolean timeToDecisionAvailable;

    // Getters and setters
    public double getTimeToReviewAvg() { return timeToReviewAvg; }
    public void setTimeToReviewAvg(double timeToReviewAvg) { this.timeToReviewAvg = timeToReviewAvg; }

    public double getTimeToReviewMedian() { return timeToReviewMedian; }
    public void setTimeToReviewMedian(double timeToReviewMedian) { this.timeToReviewMedian = timeToReviewMedian; }

    public boolean isTimeToReviewAvailable() { return timeToReviewAvailable; }
    public void setTimeToReviewAvailable(boolean timeToReviewAvailable) { this.timeToReviewAvailable = timeToReviewAvailable; }

    public String getTimeToReviewDesc() { return timeToReviewDesc; }
    public void setTimeToReviewDesc(String timeToReviewDesc) { this.timeToReviewDesc = timeToReviewDesc; }

    public double getTimeToShortlistAvg() { return timeToShortlistAvg; }
    public void setTimeToShortlistAvg(double timeToShortlistAvg) { this.timeToShortlistAvg = timeToShortlistAvg; }

    public double getTimeToShortlistMedian() { return timeToShortlistMedian; }
    public void setTimeToShortlistMedian(double timeToShortlistMedian) { this.timeToShortlistMedian = timeToShortlistMedian; }

    public boolean isTimeToShortlistAvailable() { return timeToShortlistAvailable; }
    public void setTimeToShortlistAvailable(boolean timeToShortlistAvailable) { this.timeToShortlistAvailable = timeToShortlistAvailable; }

    public double getTimeToInterviewAvg() { return timeToInterviewAvg; }
    public void setTimeToInterviewAvg(double timeToInterviewAvg) { this.timeToInterviewAvg = timeToInterviewAvg; }

    public double getTimeToInterviewMedian() { return timeToInterviewMedian; }
    public void setTimeToInterviewMedian(double timeToInterviewMedian) { this.timeToInterviewMedian = timeToInterviewMedian; }

    public boolean isTimeToInterviewAvailable() { return timeToInterviewAvailable; }
    public void setTimeToInterviewAvailable(boolean timeToInterviewAvailable) { this.timeToInterviewAvailable = timeToInterviewAvailable; }

    public double getTimeToDecisionAvg() { return timeToDecisionAvg; }
    public void setTimeToDecisionAvg(double timeToDecisionAvg) { this.timeToDecisionAvg = timeToDecisionAvg; }

    public double getTimeToDecisionMedian() { return timeToDecisionMedian; }
    public void setTimeToDecisionMedian(double timeToDecisionMedian) { this.timeToDecisionMedian = timeToDecisionMedian; }

    public boolean isTimeToDecisionAvailable() { return timeToDecisionAvailable; }
    public void setTimeToDecisionAvailable(boolean timeToDecisionAvailable) { this.timeToDecisionAvailable = timeToDecisionAvailable; }
}