package com.jobskills.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    // Note: @Email and @NotBlank removed to allow admin++/admin name login
    // with or without password. Normal email format still expected for non-admin.
    // Password size validation enforced in UserService.java: min 6 chars for non-admin users
    // Blank password allowed for admin name-only login
    private String password;
}