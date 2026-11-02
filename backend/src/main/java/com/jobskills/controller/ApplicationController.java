package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> getMyApplications(Authentication authentication) {
        return ResponseEntity.ok(applicationService.getSeekerApplications(extractUserId(authentication)));
    }

    // Status changes live in EmployerController, behind the EMPLOYER role and an
    // ownership check. A second copy here let any logged-in user rewrite any
    // application, including accepting their own.

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }
}
