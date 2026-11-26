package com.jobskills.controller;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.BusinessType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.*;
import com.jobskills.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicController {

    private final JobPostRepository jobPostRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final EmployerProfileRepository employerRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Readiness, not just liveness: the service is only reported UP when a query
     * against PostgreSQL succeeds. The answer carries no host, credential or
     * stack trace, so it is safe to serve publicly.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();
        boolean databaseUp;
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            databaseUp = one != null && one == 1;
        } catch (RuntimeException e) {
            databaseUp = false;
        }
        body.put("status", databaseUp ? "UP" : "DEGRADED");
        body.put("database", databaseUp ? "UP" : "DOWN");
        return databaseUp
            ? ResponseEntity.ok(body)
            : ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @GetMapping("/municipalities")
    public ResponseEntity<List<Municipality>> getMunicipalities() {
        return ResponseEntity.ok(Arrays.asList(Municipality.values()));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<JobCategory>> getCategories() {
        return ResponseEntity.ok(Arrays.asList(JobCategory.values()));
    }

    @GetMapping("/business-types")
    public ResponseEntity<List<BusinessType>> getBusinessTypes() {
        return ResponseEntity.ok(Arrays.asList(BusinessType.values()));
    }
}