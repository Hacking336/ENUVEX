package com.jobskills.dto;

import java.util.List;
import java.util.Map;

/** Skills analysis for a specific job posting. */
public class EmployerSkillsAnalysisDto {

    private String jobTitle;
    private String requiredSkills;
    private long applicantCount;
    private List<Map.Entry<String, Long>> mostCommonApplicantSkills;
    private List<SkillCoverageDto> missingRequiredSkills;
    private List<SkillCoverageDto> skillsCoverage;

    // Getters and setters
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }

    public long getApplicantCount() { return applicantCount; }
    public void setApplicantCount(long applicantCount) { this.applicantCount = applicantCount; }

    public List<Map.Entry<String, Long>> getMostCommonApplicantSkills() { return mostCommonApplicantSkills; }
    public void setMostCommonApplicantSkills(List<Map.Entry<String, Long>> mostCommonApplicantSkills) { this.mostCommonApplicantSkills = mostCommonApplicantSkills; }

    public List<SkillCoverageDto> getMissingRequiredSkills() { return missingRequiredSkills; }
    public void setMissingRequiredSkills(List<SkillCoverageDto> missingRequiredSkills) { this.missingRequiredSkills = missingRequiredSkills; }

    public List<SkillCoverageDto> getSkillsCoverage() { return skillsCoverage; }
    public void setSkillsCoverage(List<SkillCoverageDto> skillsCoverage) { this.skillsCoverage = skillsCoverage; }
}