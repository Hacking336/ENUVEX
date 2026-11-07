package com.jobskills.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    // Note: @Email and @NotBlank removed to allow admin++/admin name login
    // with or without password. Normal email format still expected for non-admin.
    private String email;

    // Password is optional for admin login (blank password allowed)
    // Required for job seeker/employer login (enforced in UserService)
    @Size(min = 6, max = 100)
    private String password;
}