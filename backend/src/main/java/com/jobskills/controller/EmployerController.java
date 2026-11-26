package com.jobskills.controller;

import org.springframework.web.multipart.MultipartFile;
import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.VerificationStatus;
import com.jobskills.repository.ApplicationStatusHistoryRepository;
import com.jobskills.repository.EmployerProfileRepository;
import com.jobskills.repository.JobApplicationRepository;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.repository.UserRepository;
import com.jobskills.service.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/employers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYER')")
public class EmployerController {

    private final UserService userService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final EmployerAnalyticsService employerAnalyticsService;
    private final JobPostRepository jobPostRepository;
    private final JobApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final EmployerProfileRepository employerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /** The company profile plus the account email and verification state, which
     * the employer entity itself does not expose.
     */
    @GetMapping("/profile")
    public ResponseEntity<EmployerProfileDto> getProfile(Authentication authentication) {
        Long userId = extractUserId(authentication);
        EmployerProfile profile = userService.getEmployerProfile(userId);
        EmployerProfileDto dto = new EmployerProfileDto();
        dto.setId(profile.getId());
        dto.setBusinessName(profile.getBusinessName());
        dto.setEmployerName(profile.getEmployerName());
        dto.setContactNumber(profile.getContactNumber());
        dto.setBusinessType(profile.getBusinessType());
        dto.setMunicipality(profile.getMunicipality());
        dto.setBarangay(profile.getBarangay());
        dto.setBusinessAddress(profile.getBusinessAddress());
        dto.setBusinessDescription(profile.getBusinessDescription());
        dto.setLogo(profile.getLogo());
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setVerificationStatus(profile.getVerificationStatus());
        userRepository.findById(userId).ifPresent(user -> {
            dto.setEmail(user.getEmail());
            dto.setVerified(user.isVerified());
            dto.setActive(user.isActive());
        });
        return ResponseEntity.ok(dto);
    }

    /**
     * The employer asks to be verified. This only moves an unverified, rejected
     * or information-needed profile into PENDING; it can never set VERIFIED,
     * which is an administrator decision.
     */
    @PostMapping("/verification-request")
    @Transactional
    public ResponseEntity<Map<String, String>> requestVerification(Authentication authentication) {
        Long userId = extractUserId(authentication);
        EmployerProfile profile = userService.getEmployerProfile(userId);

        if (profile.getVerificationStatus() == VerificationStatus.PENDING) {
            return ResponseEntity.ok(Map.of("status", "PENDING",
                "message", "Your verification request is already with the review team."));
        }
        if (profile.getVerificationStatus() == VerificationStatus.VERIFIED) {
            return ResponseEntity.ok(Map.of("status", "VERIFIED",
                "message", "Your account is already verified."));
        }

        profile.setVerificationStatus(VerificationStatus.PENDING);
        employerRepository.save(profile);

        userRepository.findAll().stream()
            .filter(user -> user.getUserType() == com.jobskills.model.enums.UserType.ADMIN)
            .filter(User::isActive)
            .forEach(admin -> notificationService.notify(admin,
                NotificationService.EMPLOYER_VERIFICATION,
                "Employer requested verification",
                profile.getBusinessName() + " asked to be verified. Review the company details in the verification queue.",
                "/admin/employers.html", "EMPLOYER_PROFILE", profile.getId(),
                "verification-request-" + profile.getId() + "-admin-" + admin.getId()));

        return ResponseEntity.ok(Map.of("status", "PENDING",
            "message", "Request submitted. An administrator will review your company details."));
    }

    /** Updates only the company fields. The registration bean is reused, but its
     * @Valid rules (a required email and password) do not apply to an update:
     * UserService ignores both and validates each field it actually writes.
     */
    @PutMapping("/profile")
    public ResponseEntity<EmployerProfile> updateProfile(
            Authentication authentication,
            @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return ResponseEntity.ok(userService.updateEmployerProfile(extractUserId(authentication), request, logo));
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobPost> postJob(@RequestBody JobPostRequest request, Authentication authentication) {
        return ResponseEntity.status(201).body(jobService.createJob(extractEmployerId(authentication), request));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<JobPost> updateJob(@PathVariable Long id, @RequestBody JobPostRequest request,
                                           Authentication authentication) {
        return ResponseEntity.ok(jobService.updateJob(id, extractEmployerId(authentication), request));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable Long id, Authentication authentication) {
        jobService.deleteJob(id, extractEmployerId(authentication));
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/my-jobs")
    public ResponseEntity<List<JobPost>> getMyJobs(Authentication authentication) {
        return ResponseEntity.ok(jobService.getJobsByEmployer(extractEmployerId(authentication)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsDto> getDashboard(Authentication authentication) {
        Long employerId = extractEmployerId(authentication);
        DashboardStatsDto stats = new DashboardStatsDto();
        stats.setActiveJobPosts(jobService.getJobsByEmployer(employerId).stream()
            .filter(JobPost::isActive)
            .count());
        stats.setTotalApplications(applicationService.countApplicationsForEmployer(employerId));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/analytics/overview")
    public ResponseEntity<EmployerAnalyticsOverviewDto> getAnalyticsOverview(Authentication authentication,
            @RequestParam(required = false) String dateRange) {
        Long employerId = extractEmployerId(authentication);
        EmployerAnalyticsOverviewDto overview = employerAnalyticsService.overview(employerId, dateRange);
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/analytics/job-performance")
    public ResponseEntity<Page<EmployerJobPerformanceDto>> jobPerformance(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String municipality,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String dateRange) {

        Long employerId = extractEmployerId(authentication);
        Page<EmployerJobPerformanceDto> performance = employerAnalyticsService.jobPerformance(
            employerId, sortBy, sortDir, municipality, employmentType, statusFilter, page, size, dateRange);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/analytics/funnel")
    public ResponseEntity<EmployerApplicantFunnelDto> applicantFunnel(Authentication authentication,
            @RequestParam(required = false) String dateRange) {
        Long employerId = extractEmployerId(authentication);
        EmployerApplicantFunnelDto funnel = employerAnalyticsService.funnel(employerId, dateRange);
        return ResponseEntity.ok(funnel);
    }

    @GetMapping("/analytics/response-metrics")
    public ResponseEntity<EmployerResponseMetricsDto> responseMetrics(Authentication authentication,
            @RequestParam(required = false) String dateRange) {
        Long employerId = extractEmployerId(authentication);
        EmployerResponseMetricsDto metrics = employerAnalyticsService.responseMetrics(employerId, dateRange);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/analytics/matching")
    public ResponseEntity<EmployerMatchingInsightsDto> matchingInsights(Authentication authentication,
            @RequestParam(required = false) String dateRange) {
        Long employerId = extractEmployerId(authentication);
        EmployerMatchingInsightsDto insights = employerAnalyticsService.matchingInsights(employerId, dateRange);
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/analytics/skills/{jobId}")
    public ResponseEntity<EmployerSkillsAnalysisDto> skillsAnalysis(
            @PathVariable Long jobId,
            Authentication authentication,
            @RequestParam(required = false) String dateRange) {

        Long employerId = extractEmployerId(authentication);
        EmployerSkillsAnalysisDto analysis = employerAnalyticsService.skillsAnalysis(employerId, jobId, dateRange);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/analytics/geographic")
    public ResponseEntity<EmployerGeographicInsightsDto> geographicInsights(Authentication authentication,
            @RequestParam(required = false) String dateRange) {
        Long employerId = extractEmployerId(authentication);
        EmployerGeographicInsightsDto insights = employerAnalyticsService.geographicInsights(employerId, dateRange);
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/analytics/insights")
    public ResponseEntity<List<EmployerInsightDto>> recruitmentInsights(Authentication authentication,
            @RequestParam(required = false) String dateRange) {
        Long employerId = extractEmployerId(authentication);
        List<EmployerInsightDto> insights = employerAnalyticsService.insights(employerId, dateRange);
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/analytics/export")
    public ResponseEntity<EmployerAnalyticsExportDto> exportAnalytics(
            @RequestParam(required = false) String dateRange,
            Authentication authentication) {

        Long employerId = extractEmployerId(authentication);
        EmployerAnalyticsExportDto export = employerAnalyticsService.export(employerId, dateRange);
        return ResponseEntity.ok(export);
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private Long extractEmployerId(Authentication authentication) {
        return userService.getEmployerProfile(extractUserId(authentication)).getId();
    }
}