package com.jobskills.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A learning resource an administrator has entered and verified. JSM never
 * generates courses, providers or URLs, so an empty table honestly means "no
 * verified learning resources are available".
 */
@Entity
@Table(name = "learning_resources")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class LearningResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The skill as the administrator wrote it. */
    @Column(nullable = false, length = 150)
    private String skillName;

    /** MatchingEngine.normalizeSkill(skillName), so lookups ignore case and spacing. */
    @Column(nullable = false, length = 150)
    private String skillKey;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(nullable = false, length = 200)
    private String provider;

    /** Administrator-supplied URL; validated to be http(s) before it is stored. */
    @Column(nullable = false, length = 500)
    private String url;

    private BigDecimal cost;

    @Column(name = "is_free", nullable = false)
    private boolean free;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    private String duration;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String availability;

    /** The date the administrator last confirmed the resource still exists. */
    private LocalDate verifiedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
