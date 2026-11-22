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
        stats.setTotalJobSeekers(userRepository.countByUserType(UserType.JOB_SEEKER));
        stats.setTotalEmployers(userRepository.countByUserType(UserType.EMPLOYER));
        stats.setActiveJobPosts(jobPostRepository.countByIsActiveTrue());
        stats.setTotalApplications(applicationRepository.count());
        stats.setHiredApplicants((long) applicationRepository.findByApplicationStatus(ApplicationStatus.ACCEPTED).size());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    /**
     * Employer rows for the verification screen. The account id is included
     * because verify/deactivate act on the user, and the entity hides it.
     */
    @GetMapping("/employers")
    public ResponseEntity<List<AdminEmployerDto>> getAllEmployers() {
        List<AdminEmployerDto> employers = employerRepository.findAll().stream()
            .map(this::mapEmployer)
            .collect(Collectors.toList());
        return ResponseEntity.ok(employers);
    }

    private AdminEmployerDto mapEmployer(EmployerProfile employer) {
        AdminEmployerDto dto = new AdminEmployerDto();
        User user = employer.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setEmail(user.getEmail());
            dto.setVerified(user.isVerified());
            dto.setActive(user.isActive());
        }
        dto.setProfileId(employer.getId());
        dto.setBusinessName(employer.getBusinessName());
        dto.setEmployerName(employer.getEmployerName());
        dto.setContactNumber(employer.getContactNumber());
        dto.setBusinessType(employer.getBusinessType());
        dto.setMunicipality(employer.getMunicipality());
        dto.setBarangay(employer.getBarangay());
        dto.setBusinessAddress(employer.getBusinessAddress());
        dto.setBusinessDescription(employer.getBusinessDescription());
        dto.setLogo(employer.getLogo());
        dto.setCreatedAt(employer.getCreatedAt());
        return dto;
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
    public ResponseEntity<Page<AdminJobPostDto>> getAllJobPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(jobPostRepository.findAll(pageable).map(this::mapJobPost));
    }

    private AdminJobPostDto mapJobPost(JobPost job) {
        AdminJobPostDto dto = new AdminJobPostDto();
        dto.setId(job.getId());
        dto.setJobTitle(job.getJobTitle());
        dto.setCategory(job.getJobCategory());
        dto.setEmploymentType(job.getEmploymentType());
        dto.setMunicipality(job.getMunicipality());
        dto.setBarangay(job.getBarangay());
        dto.setSalaryMin(job.getSalaryMin());
        dto.setSalaryMax(job.getSalaryMax());
        dto.setAvailablePositions(job.getAvailablePositions());
        dto.setActive(job.isActive());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setEmployerName(job.getEmployer() != null ? job.getEmployer().getBusinessName() : null);
        dto.setApplicantCount(applicationRepository.findByJobPostId(job.getId()).size());
        return dto;
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
        report.put("totalJobSeekers", userRepository.countByUserType(UserType.JOB_SEEKER));
        report.put("totalEmployers", userRepository.countByUserType(UserType.EMPLOYER));
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