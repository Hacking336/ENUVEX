package com.jobskills.dto;

import com.jobskills.model.enums.BusinessType;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * The employer's own company profile. {@code EmployerProfile} hides its user
 * relation, so the account email and verification state are added here; the
 * company profile screen needs both.
 */
@Data
public class EmployerProfileDto {
    private Long id;
    private String email;
    private boolean verified;
    private boolean active;

    private String businessName;
    private String employerName;
    private String contactNumber;
    private BusinessType businessType;
    private Municipality municipality;
    private String barangay;
    private String businessAddress;
    private String businessDescription;
    private String logo;
    private LocalDateTime createdAt;
}
