package com.jobskills.dto;

import com.jobskills.model.enums.ApplicationStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ApplicationResponse {
    private Long id;
    private String jobTitle;
    private String companyName;
    private LocalDateTime appliedAt;
    private Double matchPercentage;
    private ApplicationStatus status;
}