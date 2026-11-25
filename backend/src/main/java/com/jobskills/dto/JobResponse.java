package com.jobskills.dto;

import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private String requiredSkills;
}