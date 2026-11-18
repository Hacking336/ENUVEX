package com.jobskills.dto;

import lombok.Data;

@Data
public class AuthRequest {
    // Fields are intentionally unannotated:
    // - "email" may hold an admin name ("admin" or "admin++") on name-only login
    // - "password" may be blank for admin name-only login
    // Format and password checks for normal users happen in UserService.
    private String email;
    private String password;
}
