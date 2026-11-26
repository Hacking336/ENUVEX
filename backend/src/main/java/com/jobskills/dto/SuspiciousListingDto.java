package com.jobskills.dto;

import lombok.Data;
import com.jobskills.model.enums.ModerationStatus;
import java.time.LocalDateTime;

/** DTO for suspicious job listing flags. */
@Data
public class SuspiciousListingDto {
    private Long id;
    private Long jobPostId;
    private String jobTitle;
    private String employerName;
    private String flaggedReason;
    private String triggeredRule;
    private ModerationStatus status;
    private LocalDateTime flaggedAt;
    private boolean dismissed;
    private String dismissReason;
    private LocalDateTime dismissedAt;
}