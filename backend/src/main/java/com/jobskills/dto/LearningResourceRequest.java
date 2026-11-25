package com.jobskills.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Administrator input for a real, verified learning resource. */
@Data
public class LearningResourceRequest {
    private String skillName;
    private String title;
    private String provider;
    private String url;
    private BigDecimal cost;
    private Boolean free;
    private Boolean online;
    private String duration;
    private String description;
    private String availability;
    private LocalDate verifiedAt;
    private Boolean active;
}
