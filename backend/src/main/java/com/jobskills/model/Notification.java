package com.jobskills.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A persisted in-app notification for one user.
 *
 * {@code dedupeKey} is unique per recipient (database-enforced), which is what
 * keeps a status change or a job recommendation from being announced twice.
 */
@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Short heading shown in the bell dropdown. */
    private String title;

    /** APPLICATION_STATUS, APPLICATION_RECEIVED, INTERVIEW_INVITE, JOB_RECOMMENDATION, ... */
    private String type;

    /** In-app destination, e.g. /job-seeker/application.html?id=12. */
    private String link;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "dedupe_key")
    private String dedupeKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
