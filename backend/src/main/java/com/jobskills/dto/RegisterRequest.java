package com.jobskills.dto;

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
    private String municipality;
    private String barangay;
    private String educationLevel;
    private String courseField;
    private String skills;
    private String workExperience;
    private String certifications;
    private String employmentTypePreference;
    private String preferredWorkSchedule;
    private String availability;
    private Double expectedSalary;

    // Employer fields
    private String businessName;
    private String employerName;
    private String businessType;
    private String businessAddress;
    private String businessDescription;
}