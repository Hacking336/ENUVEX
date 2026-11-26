package com.jobskills.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jobskills.model.enums.ReportCategory;
import com.jobskills.model.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A user report against a job posting, employer, or other concern.
 * <p>
 * Users can report suspicious job postings, fake employers, misleading information, etc.
 * Administrators review and resolve reports. Reports do NOT automatically suspend
 * accounts or delete postings - they are for human review only.
 */
@Entity
@Table(name = "reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    @JsonIgnore
    private User reporter;

    @Column(nullable = false)
    private String reportedTargetType; // "JOB_POSTING", "EMPLOYER", "USER"

    @Column(name = "reported_target_id", nullable = false)
    private Long reportedTargetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportCategory category;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "resolved_by_admin_id")
    private Long resolvedByAdminId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** True if this is a duplicate of an existing open report. */
    @Column(name = "is_duplicate", nullable = false, columnDefinition = "boolean default false")
    private boolean isDuplicate = false;
}