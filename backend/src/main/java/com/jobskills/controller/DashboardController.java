package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final JobService jobService;
    private final ApplicationService applicationService;
    private final UserService userService;

    @GetMapping("/job-seekers/dashboard")
    public ResponseEntity<DashboardStatsDto> getJobSeekerDashboard(Authentication authentication) {
        Long userId = extractUserId(authentication);
        var stats = new DashboardStatsDto();
        stats.setTotalApplications(applicationService.getSeekerApplications(userId).size());
        stats.setActiveJobPosts(1);
        return ResponseEntity.ok(stats);
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