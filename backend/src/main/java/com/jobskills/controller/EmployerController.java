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

import java.util.*;

@RestController
@RequestMapping("/api/employers")
@RequiredArgsConstructor
public class EmployerController {

    private final UserService userService;
    private final JobService jobService;
    private final ApplicationService applicationService;

    @GetMapping("/profile")
    public ResponseEntity<EmployerProfile> getProfile(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.getEmployerProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<EmployerProfile> updateProfile(
            Authentication authentication,
            @Valid @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateEmployerProfile(userId, request, logo));
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobPost> postJob(@RequestBody JobPost job, Authentication authentication) {
        Long employerId = extractEmployerId(authentication);
        return ResponseEntity.status(201).body(jobService.createJob(employerId, job));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<JobPost> updateJob(@PathVariable Long id, @RequestBody JobPost job, Authentication authentication) {
        return ResponseEntity.ok(jobService.updateJob(id, job));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable Long id, Authentication authentication) {
        Long employerId = extractEmployerId(authentication);
        jobService.deleteJob(id, employerId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/my-jobs")
    public ResponseEntity<List<JobPost>> getMyJobs(Authentication authentication) {
        Long employerId = extractEmployerId(authentication);
        return ResponseEntity.ok(jobService.getJobsByEmployer(employerId));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsDto> getDashboard(Authentication authentication) {
        Long employerId = extractEmployerId(authentication);
        var employer = userService.getEmployerProfile(employerId);
        var stats = new DashboardStatsDto();
        stats.setActiveJobPosts(jobService.getJobsByEmployer(employer.getId()).size());
        stats.setTotalApplications(applicationService.getJobApplicants(employer.getId(), "date").size());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/applicants")
    public ResponseEntity<List<ApplicantDto>> getApplicants(@RequestParam Long jobId,
                                                             @RequestParam(defaultValue = "match") String sortBy) {
        return ResponseEntity.ok(applicationService.getJobApplicants(jobId, sortBy));
    }

    @PutMapping("/applicants/{id}/status")
    public ResponseEntity<Map<String, String>> updateApplicantStatus(
            @PathVariable Long id, @RequestParam ApplicationStatus status) {
        applicationService.updateApplicationStatus(id, status);
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    @PostMapping("/applicants/{id}/shortlist")
    public ResponseEntity<Map<String, String>> shortlist(@PathVariable Long id) {
        applicationService.updateApplicationStatus(id, ApplicationStatus.SHORTLISTED);
        return ResponseEntity.ok(Map.of("status", "SHORTLISTED"));
    }

    @PostMapping("/applicants/{id}/interview")
    public ResponseEntity<Map<String, String>> scheduleInterview(@PathVariable Long id) {
        applicationService.updateApplicationStatus(id, ApplicationStatus.INTERVIEW);
        return ResponseEntity.ok(Map.of("status", "INTERVIEW"));
    }

    @PostMapping("/applicants/{id}/hire")
    public ResponseEntity<Map<String, String>> hire(@PathVariable Long id) {
        applicationService.updateApplicationStatus(id, ApplicationStatus.ACCEPTED);
        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }

    @PostMapping("/applicants/{id}/reject")
    public ResponseEntity<Map<String, String>> reject(@PathVariable Long id) {
        applicationService.updateApplicationStatus(id, ApplicationStatus.REJECTED);
        return ResponseEntity.ok(Map.of("status", "REJECTED"));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private Long extractEmployerId(Authentication authentication) {
        Long userId = extractUserId(authentication);
        var employer = userService.getEmployerProfile(userId);
        return employer.getId();
    }
}