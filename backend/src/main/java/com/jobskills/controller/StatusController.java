package com.jobskills.controller;

import com.jobskills.dto.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

    private final ApplicationService applicationService;

    @PutMapping("/applications/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<Map<String, String>> updateApplicationStatus(
            @PathVariable Long id,
            @RequestParam ApplicationStatus status) {
        applicationService.updateApplicationStatus(id, status);
        return ResponseEntity.ok(Map.of("status", status.name()));
    }
}