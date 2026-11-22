package com.jobskills.dto;

import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ApplicantDto {
    /** Application id: the id the status endpoints address. */
    private Long applicationId;
    private ApplicationStatus applicationStatus;
    private LocalDateTime appliedAt;
    private Long jobPostId;
    private String jobTitle;

    /** Job seeker profile id, used for the applicant profile screen. */
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