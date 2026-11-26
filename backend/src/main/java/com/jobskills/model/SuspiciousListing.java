package com.jobskills.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jobskills.model.enums.ModerationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A flagged suspicious job listing for administrator review.
 * <p>
 * Rule-based detection flags these for human review. A flag is NOT proof of fraud -
 * it is a reason for review. Administrators can dismiss flags as false positives
 * or approve rejection of the listing.
 */
@Entity
@Table(name = "suspicious_listings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class SuspiciousListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_post_id", nullable = false)
    @JsonIgnore
    private JobPost jobPost;

    @Column(nullable = false)
    private String flaggedReason; // Human-readable explanation

    @Column(nullable = false)
    private String triggeredRule; // The rule that was triggered

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModerationStatus status = ModerationStatus.PENDING_REVIEW;

    @Column(name = "flagged_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime flaggedAt;

    private boolean dismissed = false;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "dismiss_reason")
    private String dismissReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flagged_by_admin_id")
    @JsonIgnore
    private User flaggedByAdmin;
}