package com.jobskills.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "saved_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class SavedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_seeker_id", nullable = false)
    @JsonIgnore
    private JobSeekerProfile jobSeeker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_post_id", nullable = false)
    @JsonIgnore
    private JobPost jobPost;

    @CreationTimestamp
    private LocalDateTime savedAt;
}