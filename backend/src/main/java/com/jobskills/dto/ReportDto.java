package com.jobskills.dto;

import lombok.Data;
import com.jobskills.model.enums.ReportCategory;
import com.jobskills.model.enums.ReportStatus;
import java.time.LocalDateTime;

/** DTO for Report entities returned to admin clients. */
@Data
public class ReportDto {
    private Long id;
    private String reporterEmail;
    private String reportedTargetType;
    private Long reportedTargetId;
    private ReportCategory category;
    private String description;
    private ReportStatus status;
    private String adminNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private boolean isDuplicate;

    public void setIsDuplicate(boolean isDuplicate) {
        this.isDuplicate = isDuplicate;
    }
}