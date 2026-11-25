package com.jobskills.dto;

import com.jobskills.model.enums.ApplicationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One timeline entry. Entries always come from a stored history row, except the
 * single "current status" entry that reports a state whose transition was never
 * recorded (pre-existing applications); that entry has no timestamp and is
 * flagged so the interface can say so instead of inventing a date.
 */
@Data
public class ApplicationTimelineDto {
    private Long id;
    private ApplicationStatus status;
    private String eventType;
    private String title;
    private String description;
    private String message;
    private String actorRole;
    private String actorName;
    private LocalDateTime createdAt;
    private boolean recorded = true;
}
