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
    private boolean isActive;
    private LocalDateTime createdAt;
    private Double matchPercentage;
    private String requiredSkills;
}