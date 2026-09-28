package com.jobskills.controller;

import com.jobskills.dto.*;
import com.jobskills.model.*;
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
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