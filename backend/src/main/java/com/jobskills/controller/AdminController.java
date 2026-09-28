package com.jobskills.controller;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.*;
import com.jobskills.repository.*;
import com.jobskills.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final EmployerProfileRepository employerRepository;
    private final JobPostRepository jobPostRepository;
    private final JobApplicationRepository applicationRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsDto> getDashboard() {
        DashboardStatsDto stats = new DashboardStatsDto();
        stats.setTotalJobSeekers(userRepository.countByType(UserType.JOB_SEEKER));
        stats.setTotalEmployers(userRepository.countByType(UserType.EMPLOYER));
        stats.setActiveJobPosts(jobPostRepository.countByIsActiveTrue());
        stats.setTotalApplications(applicationRepository.count());
        stats.setHiredApplicants((long) applicationRepository.findByApplicationStatus(ApplicationStatus.ACCEPTED).size());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/employers")
    public ResponseEntity<List<EmployerProfile>> getAllEmployers() {
        return ResponseEntity.ok(employerRepository.findAll());
    }

    @PutMapping("/employers/{id}/verify")
    public ResponseEntity<User> verifyEmployer(@PathVariable Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setVerified(true);
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<User> deactivateUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setActive(!user.isActive());
        return ResponseEntity.ok(userRepository.save(user));
    }

    @GetMapping("/job-posts")
    public ResponseEntity<Page<JobPost>> getAllJobPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(jobPostRepository.findAll(pageable));
    }

    @DeleteMapping("/job-posts/{id}")
    public ResponseEntity<Map<String, String>> deleteJobPost(@PathVariable Long id) {
        jobPostRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<JobApplication>> getAllApplications() {
        return ResponseEntity.ok(applicationRepository.findAll());
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReports() {
        Map<String, Object> report = new HashMap<>();
        report.put("totalUsers", userRepository.count());
        report.put("totalJobSeekers", userRepository.countByType(UserType.JOB_SEEKER));
        report.put("totalEmployers", userRepository.countByType(UserType.EMPLOYER));
        report.put("activeJobPosts", jobPostRepository.countByIsActiveTrue());
        report.put("totalApplications", applicationRepository.count());
        report.put("hiredApplicants", applicationRepository.countByApplicationStatus(ApplicationStatus.ACCEPTED));

        // Job posts by municipality
        Map<String, Long> postsByMunicipality = Arrays.stream(Municipality.values())
            .collect(Collectors.toMap(
                Municipality::name,
                m -> (long) jobPostRepository.countByMunicipality(m)
            ));
        report.put("jobPostsByMunicipality", postsByMunicipality);

        return ResponseEntity.ok(report);
    }

    @GetMapping("/municipalities")
    public ResponseEntity<List<Municipality>> getMunicipalities() {
        return ResponseEntity.ok(Arrays.asList(Municipality.values()));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();
        for (JobCategory category : JobCategory.values()) {
            Map<String, Object> cat = new HashMap<>();
            cat.put("name", category.name());
            cat.put("count", jobPostRepository.countByJobCategory(category));
            categories.add(cat);
        }
        return ResponseEntity.ok(categories);
    }
}