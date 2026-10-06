package com.jobskills.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jobskills.model.enums.BusinessType;
import com.jobskills.model.enums.Municipality;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "employer_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class EmployerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String employerName;

    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Municipality municipality;

    private String barangay;

    @Column(columnDefinition = "TEXT")
    private String businessAddress;

    @Column(columnDefinition = "TEXT")
    private String businessDescription;

    private String logo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;
}