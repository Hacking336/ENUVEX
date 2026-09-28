package com.jobskills.dto;

import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplicantDto {
    private Long id;
    private String fullName;
    private String profilePhoto;
    private String skills;
    private String education;
    private String workExperience;
    private String certifications;
    private Municipality location;
    private String barangay;
    private String availability;
    private EmploymentType employmentPreference;
    private BigDecimal expectedSalary;
    private Double matchPercentage;
    private String contactNumber;
}