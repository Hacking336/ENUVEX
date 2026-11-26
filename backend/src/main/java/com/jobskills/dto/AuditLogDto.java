package com.jobskills.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** DTO for AuditLog entities returned to admin clients. */
@Data
public class AuditLogDto {
    private Long id;
    private String actionType;
    private String adminUserEmail;
    private String targetType;
    private Long targetId;
    private String reason;
    private String previousState;
    private String newState;
    private LocalDateTime createdAt;
}