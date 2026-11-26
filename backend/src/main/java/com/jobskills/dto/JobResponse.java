package com.jobskills.dto;

import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.ModerationStatus;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class JobResponse {
    private Long id;
    private String jobTitle;
    private String companyName;
    private JobCategory category;
    private String description;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String employmentType;
    private String workSchedule;
    private int availablePositions;
    private Municipality municipality;
    private String barangay;
    private String jobLocation;
    /** Confirmed workplace pin; null when the employer has not placed one. */
    private Double latitude;
    private Double longitude;
    private String municipalityCode;
    private String barangayCode;
    /** Straight-line distance from the searcher, only on Near Me results. */
    private Double distanceKm;
    private boolean isActive;
    private LocalDateTime createdAt;
    private Double matchPercentage;
    private String matchClassification;
    private List<String> matchedSkills = new ArrayList<>();
    private List<String> missingSkills = new ArrayList<>();
    private List<MatchCriterionDto> matchCriteria = new ArrayList<>();
    private String requiredSkills;
    /** Review state; public search only ever returns APPROVED postings. */
    private ModerationStatus moderationStatus;
}