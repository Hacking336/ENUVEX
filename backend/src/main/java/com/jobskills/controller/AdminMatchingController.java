package com.jobskills.controller;

import com.jobskills.dto.AdminMatchingStatsDto;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.Municipality;
import com.jobskills.service.AdminMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Matching analytics for administrators. Every figure is a count or an average
 * over real job and seeker rows, filtered by the same location and employment
 * fields the rest of the admin console uses.
 */
@RestController
@RequestMapping("/api/admin/matching")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMatchingController {

    private final AdminMatchingService matchingService;

    @GetMapping("/stats")
    public ResponseEntity<AdminMatchingStatsDto> stats(
            @RequestParam(required = false) Municipality municipality,
            @RequestParam(required = false) String barangay,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(matchingService.stats(municipality, barangay, employmentType, from, to));
    }
}
