package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.service.ApplicationService;
import com.jobskills.service.JobService;
import com.jobskills.service.JwtService;
import com.jobskills.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/job-seekers")
@RequiredArgsConstructor
public class JobSeekerController {

    private final UserService userService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final JwtService jwtService;

    @GetMapping("/profile")
    public ResponseEntity<JobSeekerProfile> getProfile(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.getJobSeekerProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<JobSeekerProfile> updateProfile(
            Authentication authentication,
            @Valid @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "profilePhoto", required = false) MultipartFile photo) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateJobSeekerProfile(userId, request, photo));
    }

    @GetMapping("/profile/completion")
    public ResponseEntity<Map<String, Integer>> getProfileCompletion(Authentication authentication) {
        Long userId = extractUserId(authentication);
        JobSeekerProfile profile = userService.getJobSeekerProfile(userId);
        int completion = userService.calculateProfileCompletion(profile);
        return ResponseEntity.ok(Map.of("completion", completion));
    }

    @GetMapping("/jobs")
    public ResponseEntity<org.springframework.data.domain.Page<JobResponse>> searchJobs(
            JobSearchRequest search, Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        return ResponseEntity.ok(jobService.searchJobs(search, seekerId));
    }

    @GetMapping("/recommended")
    public ResponseEntity<org.springframework.data.domain.Page<JobResponse>> getRecommended(
            JobSearchRequest search, Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        search.setSortBy("matchPercentage");
        search.setSortDir("DESC");
        return ResponseEntity.ok(jobService.searchJobs(search, seekerId));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJobDetails(@PathVariable Long id, Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        return ResponseEntity.ok(jobService.getJobDetails(id, seekerId));
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(@RequestParam Long jobId, Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        var application = jobService.applyForJob(seekerId, jobId);
        return ResponseEntity.status(201).body(Map.of(
            "id", application.getId(),
            "status", "APPLIED",
            "matchPercentage", application.getMatchPercentage()
        ));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getApplications(Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        return ResponseEntity.ok(applicationService.getSeekerApplications(seekerId));
    }

    @PostMapping("/saved")
    public ResponseEntity<Map<String, String>> saveJob(@RequestParam Long jobId, Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        jobService.saveJob(seekerId, jobId);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @DeleteMapping("/saved/{jobId}")
    public ResponseEntity<Map<String, String>> unsaveJob(@PathVariable Long jobId, Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        jobService.unsaveJob(seekerId, jobId);
        return ResponseEntity.ok(Map.of("status", "unsaved"));
    }

    @GetMapping("/saved")
    public ResponseEntity<List<com.jobskills.model.SavedJob>> getSavedJobs(Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        return ResponseEntity.ok(jobService.getSavedJobs(seekerId));
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