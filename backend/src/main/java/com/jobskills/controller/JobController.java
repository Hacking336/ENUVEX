package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.JobResponse;
import com.jobskills.dto.JobSearchRequest;
import com.jobskills.model.JobPost;
import com.jobskills.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // Public job listing - no authentication required
    @GetMapping
    public ResponseEntity<Page<JobResponse>> searchJobs(JobSearchRequest search) {
        return ResponseEntity.ok(jobService.searchJobs(search, null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobDetails(id, null));
    }

    // Protected endpoint with match percentage for authenticated seekers
    @GetMapping("/with-match")
    public ResponseEntity<Page<JobResponse>> searchJobsWithMatch(
            JobSearchRequest search, Authentication authentication) {
        Long seekerId = extractUserId(authentication);
        return ResponseEntity.ok(jobService.searchJobs(search, seekerId));
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