package com.jobskills.dto;

import com.jobskills.model.enums.UserType;
import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String userType;
    private String fullName;
    private boolean isVerified;
    private Long userId;

    public AuthResponse(String token, String refreshToken, String userType, String fullName, boolean isVerified, Long userId) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.userType = userType;
        this.fullName = fullName;
        this.isVerified = isVerified;
        this.userId = userId;
    }
}