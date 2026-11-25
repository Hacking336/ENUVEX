package com.jobskills.dto;

import java.util.Map;

/** Geographic applicant insights for employer analytics. */
public class EmployerGeographicInsightsDto {

    private Map<String, Long> applicantsByMunicipality;
    private Map<String, Long> applicantsByBarangay;
    private Map<String, Double> averageMatchScoreByMunicipality;
    private Map<String, Long> applicantsByEmploymentType;

    // Getters and setters
    public Map<String, Long> getApplicantsByMunicipality() { return applicantsByMunicipality; }
    public void setApplicantsByMunicipality(Map<String, Long> applicantsByMunicipality) { this.applicantsByMunicipality = applicantsByMunicipality; }

    public Map<String, Long> getApplicantsByBarangay() { return applicantsByBarangay; }
    public void setApplicantsByBarangay(Map<String, Long> applicantsByBarangay) { this.applicantsByBarangay = applicantsByBarangay; }

    public Map<String, Double> getAverageMatchScoreByMunicipality() { return averageMatchScoreByMunicipality; }
    public void setAverageMatchScoreByMunicipality(Map<String, Double> averageMatchScoreByMunicipality) { this.averageMatchScoreByMunicipality = averageMatchScoreByMunicipality; }

    public Map<String, Long> getApplicantsByEmploymentType() { return applicantsByEmploymentType; }
    public void setApplicantsByEmploymentType(Map<String, Long> applicantsByEmploymentType) { this.applicantsByEmploymentType = applicantsByEmploymentType; }
}