package com.jobskills.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A skill the seeker is interested in learning. This is private to the seeker
 * and never changes their profile; adding the skill stays an explicit action
 * on the Skills page.
 */
@Entity
@Table(name = "skill_learning_interests",
    uniqueConstraints = @UniqueConstraint(name = "unique_learning_interest",
        columnNames = {"seeker_profile_id", "skill_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class SkillLearningInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seeker_profile_id", nullable = false)
    private JobSeekerProfile seeker;

    @Column(nullable = false, length = 150)
    private String skillName;

    @Column(nullable = false, length = 150)
    private String skillKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
