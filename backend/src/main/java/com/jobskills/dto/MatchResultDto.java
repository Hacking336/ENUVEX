package com.jobskills.dto;

import lombok.Data;

@Data
public class MatchResultDto {
    private double skillsMatch;
    private double educationMatch;
    private double experienceMatch;
    private double certificationsMatch;
    private double locationMatch;
    private double availabilityMatch;
    private double employmentTypeMatch;
    private double salaryMatch;
    private double overallMatch;
}