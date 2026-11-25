package com.jobskills.dto;

import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An application as the job seeker's tracking list shows it. */
@Data
public class ApplicationResponse {
    private Long id;
    private Long jobPostId;
    private String jobTitle;
    private String companyName;
    private LocalDateTime appliedAt;
    private Double matchPercentage;
    private String matchClassification;
    private ApplicationStatus status;
    private String statusLabel;

    private Municipality municipality;
    private String barangay;
    private String employmentType;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;

    /** Time of the latest recorded event, or the application date for old rows. */
    private LocalDateTime lastUpdatedAt;
    private String latestEventTitle;
    private String latestEventDescription;

    private InterviewDto interview;
    private boolean canWithdraw;
}
