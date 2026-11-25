package com.jobskills.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** One persisted in-app notification, shaped for the bell and the inbox page. */
@Data
public class NotificationDto {
    private Long id;
    private String title;
    private String message;
    private String type;
    private String link;
    private String referenceType;
    private Long referenceId;
    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
