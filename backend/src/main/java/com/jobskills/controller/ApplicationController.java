package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.model.enums.UserType;
import com.jobskills.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Application tracking for both sides of a hiring workflow.
 *
 * Reads resolve the caller from the token and check ownership in the service, so
 * knowing an application id never grants access to it. Writes are role-gated:
 * only an employer can change a status or manage an interview, and only the job
 * seeker who owns the application can withdraw it.
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> getMyApplications(Authentication authentication) {
        return ResponseEntity.ok(applicationService.getSeekerApplications(extractUserId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDetailDto> getApplication(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(applicationService.getApplication(id, extractUserId(authentication), userType(authentication)));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<ApplicationTimelineDto>> getTimeline(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(applicationService.getTimeline(id, extractUserId(authentication), userType(authentication)));
    }

    @GetMapping("/{id}/interview")
    public ResponseEntity<InterviewDto> getInterview(@PathVariable Long id, Authentication authentication) {
        InterviewDto interview = applicationService.getInterview(id, extractUserId(authentication), userType(authentication));
        return interview == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(interview);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<ApplicationDetailDto> updateStatus(@PathVariable Long id,
                                                            @RequestBody StatusChangeRequest request,
                                                            Authentication authentication) {
        ApplicationStatus status = ApplicationStatus.from(request.getStatus());
        return ResponseEntity.ok(applicationService.updateStatus(
            id, extractUserId(authentication), status, request.getMessage()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public ResponseEntity<ApplicationDetailDto> withdraw(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(applicationService.withdraw(id, extractUserId(authentication)));
    }

    @PostMapping("/{id}/interview")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<ApplicationDetailDto> scheduleInterview(@PathVariable Long id,
                                                                  @RequestBody InterviewRequest request,
                                                                  Authentication authentication) {
        return ResponseEntity.status(201).body(
            applicationService.scheduleInterview(id, extractUserId(authentication), request));
    }

    @PatchMapping("/{id}/interview")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<ApplicationDetailDto> rescheduleInterview(@PathVariable Long id,
                                                                    @RequestBody InterviewRequest request,
                                                                    Authentication authentication) {
        return ResponseEntity.ok(
            applicationService.rescheduleInterview(id, extractUserId(authentication), request));
    }

    @DeleteMapping("/{id}/interview")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<ApplicationDetailDto> cancelInterview(@PathVariable Long id,
                                                                @RequestParam(required = false) String reason,
                                                                Authentication authentication) {
        return ResponseEntity.ok(
            applicationService.cancelInterview(id, extractUserId(authentication), reason));
    }

    /** Employer-facing summary used by the applicant screens. */
    @GetMapping("/job/{jobId}/applicants")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<List<ApplicantDto>> getApplicants(@PathVariable Long jobId,
                                                           @RequestParam(defaultValue = "match") String sortBy,
                                                           Authentication authentication) {
        return ResponseEntity.ok(
            applicationService.getJobApplicants(jobId, extractUserId(authentication), sortBy));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private UserType userType(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUserType();
        }
        return null;
    }
}
