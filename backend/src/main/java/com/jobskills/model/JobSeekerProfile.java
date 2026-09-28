package com.jobskills.model;

import com.jobskills.model.enums.Availability;
import com.jobskills.model.enums.EducationLevel;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.Municipality;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "job_seeker_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class JobSeekerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Municipality municipality;

    private String barangay;

    @Enumerated(EnumType.STRING)
    private EducationLevel educationLevel;

    private String courseField;

    @Column(columnDefinition = "TEXT")
    private String workExperience;

    @Column(columnDefinition = "TEXT")
    private String certifications;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentTypePreference;

    @Enumerated(EnumType.STRING)
    private String preferredWorkSchedule;

    @Enumerated(EnumType.STRING)
    private Availability availability;

    private BigDecimal expectedSalaryMin;
    private BigDecimal expectedSalaryMax;

    private String profilePhoto;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;
}