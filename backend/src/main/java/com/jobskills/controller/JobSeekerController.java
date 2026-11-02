package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.service.ApplicationService;
import com.jobskills.service.JobService;
import com.jobskills.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-seekers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JOB_SEEKER')")
public class JobSeekerController {

    private final UserService userService;
    private final JobService jobService;
    private final ApplicationService applicationService;

    @GetMapping("/profile")
    public ResponseEntity<JobSeekerProfile> getProfile(Authentication authentication) {
        return ResponseEntity.ok(userService.getJobSeekerProfile(extractUserId(authentication)));
    }

    @PutMapping("/profile")
    public ResponseEntity<JobSeekerProfile> updateProfile(
            Authentication authentication,
            @Valid @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "profilePhoto", required = false) MultipartFile photo) {
        return ResponseEntity.ok(userService.updateJobSeekerProfile(extractUserId(authentication), request, photo));
    }

    @GetMapping("/profile/completion")
    public ResponseEntity<Map<String, Integer>> getProfileCompletion(Authentication authentication) {
        JobSeekerProfile profile = userService.getJobSeekerProfile(extractUserId(authentication));
        return ResponseEntity.ok(Map.of("completion", userService.calculateProfileCompletion(profile)));
    }

    @GetMapping("/jobs")
    public ResponseEntity<org.springframework.data.domain.Page<JobResponse>> searchJobs(
            JobSearchRequest search, Authentication authentication) {
        return ResponseEntity.ok(jobService.searchJobs(search, extractUserId(authentication)));
    }

    @GetMapping("/recommended")
    public ResponseEntity<org.springframework.data.domain.Page<JobResponse>> getRecommended(
            JobSearchRequest search, Authentication authentication) {
        search.setSortBy("matchPercentage");
        return ResponseEntity.ok(jobService.searchJobs(search, extractUserId(authentication)));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJobDetails(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(jobService.getJobDetails(id, extractUserId(authentication)));
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(@RequestParam Long jobId, Authentication authentication) {
        var application = jobService.applyForJob(extractUserId(authentication), jobId);
        return ResponseEntity.status(201).body(Map.of(
            "id", application.getId(),
            "status", "APPLIED",
            "matchPercentage", application.getMatchPercentage()
        ));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getApplications(Authentication authentication) {
        return ResponseEntity.ok(applicationService.getSeekerApplications(extractUserId(authentication)));
    }

    @PostMapping("/saved")
    public ResponseEntity<Map<String, String>> saveJob(@RequestParam Long jobId, Authentication authentication) {
        jobService.saveJob(extractUserId(authentication), jobId);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @DeleteMapping("/saved/{jobId}")
    public ResponseEntity<Map<String, String>> unsaveJob(@PathVariable Long jobId, Authentication authentication) {
        jobService.unsaveJob(extractUserId(authentication), jobId);
        return ResponseEntity.ok(Map.of("status", "unsaved"));
    }

    @GetMapping("/saved")
    public ResponseEntity<List<com.jobskills.model.SavedJob>> getSavedJobs(Authentication authentication) {
        return ResponseEntity.ok(jobService.getSavedJobs(extractUserId(authentication)));
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
