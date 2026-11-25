package com.jobskills.dto;

import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Full application record with its timeline and interview, for either side. */
@Data
public class ApplicationDetailDto {
    private Long id;
    private Long jobPostId;
    private String jobTitle;
    private String companyName;
    private String jobDescription;
    private Municipality municipality;
    private String barangay;
    private String employmentType;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private LocalDateTime appliedAt;
    private LocalDateTime lastUpdatedAt;
    private Double matchPercentage;
    private String matchClassification;
    private List<String> matchedSkills = new ArrayList<>();
    private List<String> missingSkills = new ArrayList<>();
    private ApplicationStatus status;
    private String statusLabel;

    /** Job seeker profile id, for the employer's profile view. */
    private Long jobSeekerId;
    private String seekerName;
    private String seekerEmail;

    private InterviewDto interview;
    private List<ApplicationTimelineDto> timeline = new ArrayList<>();

    private boolean viewerIsSeeker;
    private boolean viewerIsEmployer;
    private boolean canWithdraw;
    private boolean canManage;
    /** Statuses the current viewer may move this application to. */
    private List<ApplicationStatus> allowedNextStatuses = new ArrayList<>();
}
