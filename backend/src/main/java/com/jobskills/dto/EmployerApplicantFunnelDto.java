package com.jobskills.dto;

/** Funnel stage counts and conversion rates. */
public class EmployerApplicantFunnelDto {

    private long applicationsReceived;
    private long reviewing;
    private long shortlisted;
    private long interviewed;
    private long accepted;
    private long rejected;
    private double[] conversionRates;
    private long[] dropOff;

    // Getters and setters
    public long getApplicationsReceived() { return applicationsReceived; }
    public void setApplicationsReceived(long applicationsReceived) { this.applicationsReceived = applicationsReceived; }

    public long getReviewing() { return reviewing; }
    public void setReviewing(long reviewing) { this.reviewing = reviewing; }

    public long getShortlisted() { return shortlisted; }
    public void setShortlisted(long shortlisted) { this.shortlisted = shortlisted; }

    public long getInterviewed() { return interviewed; }
    public void setInterviewed(long interviewed) { this.interviewed = interviewed; }

    public long getAccepted() { return accepted; }
    public void setAccepted(long accepted) { this.accepted = accepted; }

    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }

    public double[] getConversionRates() { return conversionRates; }
    public void setConversionRates(double[] conversionRates) { this.conversionRates = conversionRates; }

    public long[] getDropOff() { return dropOff; }
    public void setDropOff(long[] dropOff) { this.dropOff = dropOff; }
}