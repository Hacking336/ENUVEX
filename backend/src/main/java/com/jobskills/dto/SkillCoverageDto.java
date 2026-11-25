package com.jobskills.dto;

/** Coverage percentage for a required skill. */
public class SkillCoverageDto {

    private String skill;
    private long applicantsWithApplicantsWithSkill;
    private long totalApplicants;
    private double coveragePercentage;
    private String sampleSizeNote;

    // Getters and setters
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }

    public long getApplicantsWithApplicantsWithSkill() { return applicantsWithApplicantsWithSkill; }
    public void setApplicantsWithApplicantsWithSkill(long applicantsWithApplicantsWithSkill) { this.applicantsWithApplicantsWithSkill = applicantsWithApplicantsWithSkill; }

    public long getTotalApplicants() { return totalApplicants; }
    public void setTotalApplicants(long totalApplicants) { this.totalApplicants = totalApplicants; }

    public double getCoveragePercentage() { return coveragePercentage; }
    public void setCoveragePercentage(double coveragePercentage) { this.coveragePercentage = coveragePercentage; }

    public String getSampleSizeNote() { return sampleSizeNote; }
    public void setSampleSizeNote(String sampleSizeNote) { this.sampleSizeNote = sampleSizeNote; }
}