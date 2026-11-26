package com.jobskills.dto;

import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.ModerationStatus;
import com.jobskills.model.enums.Municipality;
import com.jobskills.model.enums.VerificationStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One job post for the admin moderation list. {@code JobPost} hides its
 * employer relation, so the company name and applicant count are resolved here
 * instead of being guessed in the browser. The description fields are included
 * so a moderator can read the whole posting before deciding.
 */
@Data
public class AdminJobPostDto {
    private Long id;
    private String jobTitle;
    private JobCategory category;
    private EmploymentType employmentType;
    private Municipality municipality;
    private String barangay;
    private String jobLocation;
    private String jobDescription;
    private String responsibilities;
    private String requiredSkills;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private int availablePositions;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String employerName;
    private Long employerUserId;
    private VerificationStatus employerVerificationStatus;
    private long applicantCount;
    private ModerationStatus moderationStatus;
}
