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

    /**
     * No @Valid here on purpose: the admin signs in with a name and no password,
     * so the password field must be allowed to be blank. UserService enforces
     * the password for job seekers and employers.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        AuthResponse response = userService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
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