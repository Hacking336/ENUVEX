package com.jobskills.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String actionType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String ipAddress;
    private String userAgent;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
