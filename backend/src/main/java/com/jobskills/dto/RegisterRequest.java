package com.jobskills.dto;

import com.jobskills.model.enums.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    private String userType; // "JOB_SEEKER" or "EMPLOYER"

    // Job Seeker fields
    private String fullName;
    private String contactNumber;
    private Municipality municipality;
    private String barangay;
    private EducationLevel educationLevel;
    private String courseField;
    private String skills;
    private String workExperience;
    private String certifications;
    private EmploymentType employmentTypePreference;
    private String preferredWorkSchedule;
    private Availability availability;
    private Double expectedSalary;

    // Employer fields
    private String businessName;
    private String employerName;
    private String businessType;
    private String businessAddress;
    private String businessDescription;
}