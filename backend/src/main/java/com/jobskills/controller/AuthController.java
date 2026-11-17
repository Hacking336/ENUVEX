package com.jobskills.controller;

import com.jobskills.dto.AuthRequest;
import com.jobskills.dto.AuthResponse;
import com.jobskills.dto.RegisterRequest;
import com.jobskills.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        // Manual validation - skip Spring @Valid to allow blank password for admin
        String email = request.getEmail();
        String password = request.getPassword();

        // Basic email check
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new AuthResponse("Email is required"));
        }

        // Check if this is an admin name-only login (blank password)
        boolean isAdminNameLogin = (email != null && (email.equalsIgnoreCase("admin") || email.equalsIgnoreCase("admin++"))) && (password == null || password.trim().isEmpty());

        if (isAdminNameLogin) {
            // Admin name-only login: skip password validation, authenticate by name
            return userService.login(email, password);
        }

        // Normal login: require valid password
        // Note: UserService.login() will validate password against hash
        return userService.login(email, password);
    }

    @PostMapping(value = "/register", consumes = {"multipart/form-data"})
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "profilePhoto", required = false) MultipartFile profilePhoto,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        AuthResponse response = userService.register(request, profilePhoto, logo);
        return ResponseEntity.status(201).body(response);
    }
}