package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.repository.UserRepository;
import com.jobskills.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/employers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYER')")
public class EmployerController {

    private final UserService userService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;

    /**
     * The company profile plus the account email and verification state, which
     * the employer entity itself does not expose.
     */
    @GetMapping("/profile")
    public ResponseEntity<EmployerProfileDto> getProfile(Authentication authentication) {
        Long userId = extractUserId(authentication);
        EmployerProfile profile = userService.getEmployerProfile(userId);
        EmployerProfileDto dto = new EmployerProfileDto();
        dto.setId(profile.getId());
        dto.setBusinessName(profile.getBusinessName());
        dto.setEmployerName(profile.getEmployerName());
        dto.setContactNumber(profile.getContactNumber());
        dto.setBusinessType(profile.getBusinessType());
        dto.setMunicipality(profile.getMunicipality());
        dto.setBarangay(profile.getBarangay());
        dto.setBusinessAddress(profile.getBusinessAddress());
        dto.setBusinessDescription(profile.getBusinessDescription());
        dto.setLogo(profile.getLogo());
        dto.setCreatedAt(profile.getCreatedAt());
        userRepository.findById(userId).ifPresent(user -> {
            dto.setEmail(user.getEmail());
            dto.setVerified(user.isVerified());
            dto.setActive(user.isActive());
        });
        return ResponseEntity.ok(dto);
    }

    /**
     * Updates only the company fields. The registration bean is reused, but its
     * @Valid rules (a required email and password) do not apply to an update:
     * UserService ignores both and validates each field it actually writes.
     */
    @PutMapping("/profile")
    public ResponseEntity<EmployerProfile> updateProfile(
            Authentication authentication,
            @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return ResponseEntity.ok(userService.updateEmployerProfile(extractUserId(authentication), request, logo));
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobPost> postJob(@RequestBody JobPost job, Authentication authentication) {
        return ResponseEntity.status(201).body(jobService.createJob(extractEmployerId(authentication), job));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<JobPost> updateJob(@PathVariable Long id, @RequestBody JobPost job,
                                             Authentication authentication) {
        return ResponseEntity.ok(jobService.updateJob(id, extractEmployerId(authentication), job));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable Long id, Authentication authentication) {
        jobService.deleteJob(id, extractEmployerId(authentication));
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/my-jobs")
    public ResponseEntity<List<JobPost>> getMyJobs(Authentication authentication) {
        return ResponseEntity.ok(jobService.getJobsByEmployer(extractEmployerId(authentication)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsDto> getDashboard(Authentication authentication) {
        Long employerId = extractEmployerId(authentication);
        DashboardStatsDto stats = new DashboardStatsDto();
        stats.setActiveJobPosts(jobService.getJobsByEmployer(employerId).stream()
            .filter(JobPost::isActive)
            .count());
        stats.setTotalApplications(applicationService.countApplicationsForEmployer(employerId));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/applicants")
    public ResponseEntity<List<ApplicantDto>> getApplicants(@RequestParam Long jobId,
                                                            @RequestParam(defaultValue = "match") String sortBy,
                                                            Authentication authentication) {
        return ResponseEntity.ok(
            applicationService.getJobApplicants(jobId, extractEmployerId(authentication), sortBy));
    }

    @PutMapping("/applicants/{id}/status")
    public ResponseEntity<Map<String, String>> updateApplicantStatus(
            @PathVariable Long id, @RequestParam ApplicationStatus status, Authentication authentication) {
        applicationService.updateApplicationStatus(id, extractEmployerId(authentication), status);
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    @PostMapping("/applicants/{id}/shortlist")
    public ResponseEntity<Map<String, String>> shortlist(@PathVariable Long id, Authentication authentication) {
        applicationService.updateApplicationStatus(id, extractEmployerId(authentication), ApplicationStatus.SHORTLISTED);
        return ResponseEntity.ok(Map.of("status", "SHORTLISTED"));
    }

    @PostMapping("/applicants/{id}/interview")
    public ResponseEntity<Map<String, String>> scheduleInterview(@PathVariable Long id, Authentication authentication) {
        applicationService.updateApplicationStatus(id, extractEmployerId(authentication), ApplicationStatus.INTERVIEW);
        return ResponseEntity.ok(Map.of("status", "INTERVIEW"));
    }

    @PostMapping("/applicants/{id}/hire")
    public ResponseEntity<Map<String, String>> hire(@PathVariable Long id, Authentication authentication) {
        applicationService.updateApplicationStatus(id, extractEmployerId(authentication), ApplicationStatus.ACCEPTED);
        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }

    @PostMapping("/applicants/{id}/reject")
    public ResponseEntity<Map<String, String>> reject(@PathVariable Long id, Authentication authentication) {
        applicationService.updateApplicationStatus(id, extractEmployerId(authentication), ApplicationStatus.REJECTED);
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
        return userService.getEmployerProfile(extractUserId(authentication)).getId();
    }
}
