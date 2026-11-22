package com.jobskills.dto;

import com.jobskills.model.enums.BusinessType;
import com.jobskills.model.enums.Municipality;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One employer row for the admin verification screen. The entity hides its
 * {@code user} relation, so the account id, email and verification flag have to
 * travel in the payload: {@code PUT /api/admin/employers/{id}/verify} addresses
 * the user, not the profile.
 */
@Data
public class AdminEmployerDto {
    private Long userId;
    private String email;
    private boolean verified;
    private boolean active;

    private Long profileId;
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
