package com.jobskills.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * The current interview arrangement for an application.
 *
 * One row per application: a reschedule rewrites the row, and the original
 * schedule stays visible in the application history, so the timeline keeps both
 * the old and the new appointment.
 */
@Entity
@Table(name = "application_interviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class ApplicationInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Column(name = "interview_at", nullable = false)
    private LocalDateTime interviewAt;

    /** IN_PERSON, ONLINE or PHONE. */
    @Column(name = "interview_type", nullable = false)
    private String interviewType;

    private String location;

    @Column(name = "meeting_link")
    private String meetingLink;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "employer_message", columnDefinition = "TEXT")
    private String employerMessage;

    /** SCHEDULED, RESCHEDULED or CANCELLED. */
    @Column(nullable = false)
    private String status;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
