package com.jobskills.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** A private learning-interest row. Only its owner can read it. */
@Data
public class LearningInterestDto {
    private Long id;
    private String skill;
    private String skillKey;
    private LocalDateTime createdAt;
}
