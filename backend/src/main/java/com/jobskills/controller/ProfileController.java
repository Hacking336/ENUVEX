package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/job-seeker/completion")
    public ResponseEntity<Map<String, Integer>> getJobSeekerCompletion(Authentication authentication) {
        Long userId = extractUserId(authentication);
        var profile = userService.getJobSeekerProfile(userId);
        return ResponseEntity.ok(Map.of("completion", userService.calculateProfileCompletion(profile)));
    }

    @PutMapping("/job-seeker")
    public ResponseEntity<JobSeekerProfile> updateJobSeeker(
            Authentication authentication,
            @Valid @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "profilePhoto", required = false) MultipartFile photo) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateJobSeekerProfile(userId, request, photo));
    }

    @PutMapping("/employer")
    public ResponseEntity<EmployerProfile> updateEmployer(
            Authentication authentication,
            @Valid @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateEmployerProfile(userId, request, logo));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }
}