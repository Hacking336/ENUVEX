package com.jobskills.dto;

import lombok.Data;

@Data
public class AuthRequest {
    // Note: @Email and @NotBlank removed to allow admin++/admin name login
    // with or without password. Normal email format still expected for non-admin.
    // Blank password allowed for admin name-only login
    private String password;
}