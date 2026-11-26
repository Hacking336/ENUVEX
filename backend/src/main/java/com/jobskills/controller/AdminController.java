package com.jobskills.controller;

import com.jobskills.config.SecurityUtils;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Administrator console API.
 *
 * <p>Every endpoint is admin-only at the controller level, so a role check
 * cannot be skipped from the browser. Every state-changing moderation call
 * records who did it in {@code audit_logs} using the authenticated administrator
 * from the security context — never a user id sent by the client.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final EmployerProfileRepository employerRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final JobPostRepository jobPostRepository;
    private final JobApplicationRepository applicationRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final SuspiciousListingRepository suspiciousListingRepository;
    private final NotificationService notificationService;
    private final SuspiciousListingService suspiciousListingService;
    private final JobService jobService;
    private final SkillsAnalyticsService skillsAnalyticsService;

    /* =====================================================================
       Phase 5 statistics (unchanged endpoints)
       ===================================================================== */

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsDto> getDashboard() {
        DashboardStatsDto stats = new DashboardStatsDto();
        stats.setTotalJobSeekers(userRepository.countByUserType(UserType.JOB_SEEKER));
        stats.setTotalEmployers(userRepository.countByUserType(UserType.EMPLOYER));
        stats.setActiveJobPosts(jobPostRepository.countByIsActiveTrue());
        stats.setTotalApplications(applicationRepository.count());
        stats.setHiredApplicants((long) applicationRepository.findByApplicationStatus(ApplicationStatus.ACCEPTED).size());
        stats.setApplicationsByStatus(applicationCounts());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/applications")
    public ResponseEntity<List<JobApplication>> getAllApplications() {
        return ResponseEntity.ok(applicationRepository.findAll());
    }

    /** Phase 5 analytics summary; the moderation queue lives on its own path. */
    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReports() {
        Map<String, Object> report = new HashMap<>();
        report.put("totalUsers", userRepository.count());
        report.put("totalJobSeekers", userRepository.countByUserType(UserType.JOB_SEEKER));
        report.put("totalEmployers", userRepository.countByUserType(UserType.EMPLOYER));
        report.put("activeJobPosts", jobPostRepository.countByIsActiveTrue());
        report.put("totalApplications", applicationRepository.count());
        report.put("hiredApplicants", applicationRepository.countByApplicationStatus(ApplicationStatus.ACCEPTED));
        report.put("applicationsByStatus", applicationCounts());

        Map<String, Long> postsByMunicipality = Arrays.stream(Municipality.values())
            .collect(Collectors.toMap(Municipality::name,
                m -> (long) jobPostRepository.countByMunicipality(m)));
        report.put("jobPostsByMunicipality", postsByMunicipality);
        return ResponseEntity.ok(report);
    }

    private Map<String, Long> applicationCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status.name(), applicationRepository.countByApplicationStatus(status));
        }
        return counts;
    }

    /* ---------- skills analytics and learning resources --------------- */

    @GetMapping("/skills-analytics")
    public ResponseEntity<SkillsAnalyticsDto> getSkillsAnalytics(
            @RequestParam(required = false) Municipality municipality,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) JobCategory category,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(skillsAnalyticsService.analytics(municipality, employmentType, category, days));
    }

    @GetMapping("/skills-demand")
    public ResponseEntity<SkillsAnalyticsDto> getSkillsDemand(
            @RequestParam(required = false) Municipality municipality,
            @RequestParam(required = false) String barangay,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) JobCategory category,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(skillsAnalyticsService.demand(municipality, barangay,
            employmentType, category, days));
    }

    @GetMapping("/learning-resources")
    public ResponseEntity<List<LearningResource>> getLearningResources() {
        return ResponseEntity.ok(skillsAnalyticsService.allResources());
    }

    @PostMapping("/learning-resources")
    public ResponseEntity<LearningResource> createLearningResource(@RequestBody LearningResourceRequest request) {
        return ResponseEntity.status(201).body(skillsAnalyticsService.createResource(request));
    }

    @PutMapping("/learning-resources/{id}")
    public ResponseEntity<LearningResource> updateLearningResource(
            @PathVariable Long id, @RequestBody LearningResourceRequest request) {
        return ResponseEntity.ok(skillsAnalyticsService.updateResource(id, request));
    }

    @DeleteMapping("/learning-resources/{id}")
    public ResponseEntity<Map<String, String>> deleteLearningResource(@PathVariable Long id) {
        skillsAnalyticsService.deleteResource(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/municipalities")
    public ResponseEntity<List<Municipality>> getMunicipalities() {
        return ResponseEntity.ok(Arrays.asList(Municipality.values()));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();
        for (JobCategory category : JobCategory.values()) {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("name", category.name());
            cat.put("count", jobPostRepository.countByJobCategory(category));
            categories.add(cat);
        }
        return ResponseEntity.ok(categories);
    }

    /* =====================================================================
       Moderation overview (dashboard cards)
       ===================================================================== */

    @GetMapping("/moderation-overview")
    public ResponseEntity<ModerationOverviewDto> getModerationOverview() {
        ModerationOverviewDto overview = new ModerationOverviewDto();
        overview.setTotalUsers(userRepository.count());
        overview.setTotalEmployers(userRepository.countByUserType(UserType.EMPLOYER));

        overview.setVerifiedEmployers(employerRepository.countByVerificationStatus(VerificationStatus.VERIFIED));
        overview.setPendingVerifications(employerRepository.countByVerificationStatus(VerificationStatus.PENDING));
        overview.setRejectedEmployers(employerRepository.countByVerificationStatus(VerificationStatus.REJECTED));
        overview.setNeedsInformationEmployers(
            employerRepository.countByVerificationStatus(VerificationStatus.NEEDS_INFORMATION));
        overview.setUnverifiedEmployers(employerRepository.countByVerificationStatus(VerificationStatus.UNVERIFIED));

        overview.setTotalJobPosts(jobPostRepository.count());
        overview.setApprovedJobs(jobPostRepository.countByModerationStatus(ModerationStatus.APPROVED));
        overview.setPendingReviewJobs(jobPostRepository.countByModerationStatus(ModerationStatus.PENDING_REVIEW));
        overview.setChangesRequestedJobs(jobPostRepository.countByModerationStatus(ModerationStatus.CHANGES_REQUESTED));
        overview.setRejectedJobs(jobPostRepository.countByModerationStatus(ModerationStatus.REJECTED));
        overview.setActiveJobs(jobPostRepository.countByIsActiveTrue());

        overview.setOpenReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.OPEN).size());
        overview.setUnderReviewReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.UNDER_REVIEW).size());
        overview.setResolvedReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.RESOLVED).size());
        overview.setDismissedReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.DISMISSED).size());

        overview.setSuspiciousListings(suspiciousListingRepository.findActiveByFlaggedAtDesc().size());
        overview.setSuspendedAccounts(userRepository.countByIsActiveFalse());
        return ResponseEntity.ok(overview);
    }

    /* =====================================================================
       Employer verification
       ===================================================================== */

    @GetMapping("/employers")
    public ResponseEntity<List<AdminEmployerDto>> getAllEmployers() {
        return ResponseEntity.ok(employerRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::mapEmployer)
            .collect(Collectors.toList()));
    }

    @GetMapping("/employers/pending")
    public ResponseEntity<List<AdminEmployerDto>> getPendingEmployers() {
        return ResponseEntity.ok(employerRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(profile -> profile.getVerificationStatus() == VerificationStatus.PENDING)
            .map(this::mapEmployer)
            .collect(Collectors.toList()));
    }

    @GetMapping("/employers/{id}")
    public ResponseEntity<AdminEmployerDto> getEmployer(@PathVariable Long id) {
        return ResponseEntity.ok(mapEmployer(requireEmployerProfile(id)));
    }

    /** Approves a company. Only this endpoint can produce a VERIFIED status. */
    @PutMapping("/employers/{id}/verify")
    public ResponseEntity<AdminEmployerDto> verifyEmployer(@PathVariable Long id) {
        EmployerProfile profile = requireEmployerProfile(id);
        User account = profile.getUser();
        String previous = profile.getVerificationStatus().name();

        profile.setVerificationStatus(VerificationStatus.VERIFIED);
        if (account != null) {
            account.setVerified(true);
            userRepository.save(account);
        }
        employerRepository.save(profile);

        audit("EMPLOYER_VERIFIED", "EMPLOYER_PROFILE", profile.getId(), previous,
            VerificationStatus.VERIFIED.name(), null);
        if (account != null) {
            notificationService.notify(account, NotificationService.EMPLOYER_VERIFIED,
                "Company verified",
                "Your company profile has been verified. Your job postings can be published once approved.",
                "/employer/profile.html", "EMPLOYER_PROFILE", profile.getId(),
                "employer-verified-" + profile.getId());
        }
        return ResponseEntity.ok(mapEmployer(profile));
    }

    @PutMapping("/employers/{id}/reject-verification")
    public ResponseEntity<AdminEmployerDto> rejectVerification(@PathVariable Long id,
                                                               @RequestParam String reason) {
        String checkedReason = requireReason(reason);
        EmployerProfile profile = requireEmployerProfile(id);
        User account = profile.getUser();
        String previous = profile.getVerificationStatus().name();

        profile.setVerificationStatus(VerificationStatus.REJECTED);
        if (account != null) {
            account.setVerified(false);
            userRepository.save(account);
        }
        employerRepository.save(profile);

        audit("EMPLOYER_VERIFICATION_REJECTED", "EMPLOYER_PROFILE", profile.getId(), previous,
            VerificationStatus.REJECTED.name(), checkedReason);
        if (account != null) {
            notificationService.notify(account, NotificationService.EMPLOYER_REJECTED,
                "Company verification rejected",
                "Your company verification was rejected. Reason: " + checkedReason,
                "/employer/profile.html", "EMPLOYER_PROFILE", profile.getId(),
                "employer-rejected-" + profile.getId());
        }
        return ResponseEntity.ok(mapEmployer(profile));
    }

    @PutMapping("/employers/{id}/info-request")
    public ResponseEntity<AdminEmployerDto> requestInformation(@PathVariable Long id,
                                                               @RequestParam String reason) {
        String checkedReason = requireReason(reason);
        EmployerProfile profile = requireEmployerProfile(id);
        User account = profile.getUser();
        String previous = profile.getVerificationStatus().name();

        profile.setVerificationStatus(VerificationStatus.NEEDS_INFORMATION);
        employerRepository.save(profile);

        audit("EMPLOYER_INFORMATION_REQUESTED", "EMPLOYER_PROFILE", profile.getId(), previous,
            VerificationStatus.NEEDS_INFORMATION.name(), checkedReason);
        if (account != null) {
            notificationService.notify(account, NotificationService.VERIFICATION_INFO_REQUESTED,
                "More information needed",
                "We need more information to verify your company. Details: " + checkedReason,
                "/employer/profile.html", "EMPLOYER_PROFILE", profile.getId(),
                "employer-info-" + profile.getId() + "-" + System.currentTimeMillis());
        }
        return ResponseEntity.ok(mapEmployer(profile));
    }

    /* =====================================================================
       Job posting moderation
       ===================================================================== */

    @GetMapping("/job-posts")
    public ResponseEntity<Page<AdminJobPostDto>> getAllJobPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(jobPostRepository.findAll(pageable).map(this::mapJobPost));
    }

    @GetMapping("/job-posts/pending-review")
    public ResponseEntity<List<AdminJobPostDto>> getPendingReviewJobs() {
        return ResponseEntity.ok(jobPostRepository.findByModerationStatus(ModerationStatus.PENDING_REVIEW).stream()
            .map(this::mapJobPost)
            .collect(Collectors.toList()));
    }

    @GetMapping("/job-posts/{id}")
    public ResponseEntity<AdminJobPostDto> getJobPost(@PathVariable Long id) {
        return ResponseEntity.ok(mapJobPost(requireJobPost(id)));
    }

    @DeleteMapping("/job-posts/{id}")
    public ResponseEntity<Map<String, String>> deleteJobPost(@PathVariable Long id) {
        JobPost job = requireJobPost(id);
        String previous = job.getModerationStatus().name();
        jobPostRepository.delete(job);
        audit("JOB_POST_DELETED", "JOB_POST", id, previous, "DELETED", null);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PutMapping("/job-posts/{id}/approve")
    public ResponseEntity<AdminJobPostDto> approveJobPost(@PathVariable Long id) {
        JobPost job = requireJobPost(id);
        String previous = job.getModerationStatus().name();
        job.setModerationStatus(ModerationStatus.APPROVED);
        JobPost saved = jobPostRepository.save(job);

        audit("JOB_POST_APPROVED", "JOB_POST", saved.getId(), previous,
            ModerationStatus.APPROVED.name(), null);

        User employerAccount = saved.getEmployer() != null ? saved.getEmployer().getUser() : null;
        if (employerAccount != null) {
            notificationService.notify(employerAccount, NotificationService.JOB_POST_APPROVED,
                "Job posting approved",
                "Your posting \"" + saved.getJobTitle() + "\" is approved and now visible to job seekers.",
                "/employer/jobs.html", "JOB_POST", saved.getId(), "job-approved-" + saved.getId());
        }
        // Matching seekers are told about the post only once it is approved.
        if (saved.isActive()) {
            jobService.announceApprovedJob(saved);
        }
        return ResponseEntity.ok(mapJobPost(saved));
    }

    @PutMapping("/job-posts/{id}/reject")
    public ResponseEntity<AdminJobPostDto> rejectJobPost(@PathVariable Long id,
                                                        @RequestParam String reason) {
        String checkedReason = requireReason(reason);
        JobPost job = requireJobPost(id);
        String previous = job.getModerationStatus().name();
        job.setModerationStatus(ModerationStatus.REJECTED);
        JobPost saved = jobPostRepository.save(job);

        audit("JOB_POST_REJECTED", "JOB_POST", saved.getId(), previous,
            ModerationStatus.REJECTED.name(), checkedReason);

        User employerAccount = saved.getEmployer() != null ? saved.getEmployer().getUser() : null;
        if (employerAccount != null) {
            notificationService.notify(employerAccount, NotificationService.JOB_POST_REJECTED,
                "Job posting rejected",
                "Your posting \"" + saved.getJobTitle() + "\" was rejected. Reason: " + checkedReason,
                "/employer/jobs.html", "JOB_POST", saved.getId(), "job-rejected-" + saved.getId());
        }
        return ResponseEntity.ok(mapJobPost(saved));
    }

    @PutMapping("/job-posts/{id}/changes-requested")
    public ResponseEntity<AdminJobPostDto> requestChanges(@PathVariable Long id,
                                                         @RequestParam String reason) {
        String checkedReason = requireReason(reason);
        JobPost job = requireJobPost(id);
        String previous = job.getModerationStatus().name();
        job.setModerationStatus(ModerationStatus.CHANGES_REQUESTED);
        JobPost saved = jobPostRepository.save(job);

        audit("JOB_POST_CHANGES_REQUESTED", "JOB_POST", saved.getId(), previous,
            ModerationStatus.CHANGES_REQUESTED.name(), checkedReason);

        User employerAccount = saved.getEmployer() != null ? saved.getEmployer().getUser() : null;
        if (employerAccount != null) {
            notificationService.notify(employerAccount, NotificationService.JOB_POST_CHANGES_REQUESTED,
                "Changes requested for job posting",
                "Your posting \"" + saved.getJobTitle() + "\" needs corrections. Details: " + checkedReason,
                "/employer/jobs.html", "JOB_POST", saved.getId(),
                "job-changes-" + saved.getId() + "-" + System.currentTimeMillis());
        }
        return ResponseEntity.ok(mapJobPost(saved));
    }

    /** Sends a rejected or changes-requested posting back into the queue. */
    @PutMapping("/job-posts/{id}/restore")
    public ResponseEntity<AdminJobPostDto> restoreJobPost(@PathVariable Long id) {
        JobPost job = requireJobPost(id);
        String previous = job.getModerationStatus().name();
        job.setModerationStatus(ModerationStatus.PENDING_REVIEW);
        JobPost saved = jobPostRepository.save(job);

        audit("JOB_POST_RESTORED", "JOB_POST", saved.getId(), previous,
            ModerationStatus.PENDING_REVIEW.name(), null);
        return ResponseEntity.ok(mapJobPost(saved));
    }

    /* =====================================================================
       Reports about listings and accounts
       ===================================================================== */

    @GetMapping("/moderation-reports")
    public ResponseEntity<List<ReportDto>> getModerationReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportCategory category,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId) {

        List<Report> reports;
        if (targetType != null && targetId != null) {
            reports = reportRepository.findByReportedTargetTypeAndReportedTargetIdOrderByCreatedAtDesc(
                targetType, targetId);
        } else if (status != null) {
            reports = reportRepository.findByStatusOrderByCreatedAtDesc(status);
        } else if (category != null) {
            reports = reportRepository.findByCategoryOrderByCreatedAtDesc(category);
        } else {
            reports = reportRepository.findAllByOrderByCreatedAtDesc();
        }
        if (status != null && category != null) {
            reports = reports.stream().filter(r -> r.getCategory() == category).collect(Collectors.toList());
        }
        return ResponseEntity.ok(reports.stream().map(this::mapReport).collect(Collectors.toList()));
    }

    @PutMapping("/moderation-reports/{id}/review")
    public ResponseEntity<ReportDto> markReportUnderReview(@PathVariable Long id) {
        Report report = requireReport(id);
        String previous = report.getStatus().name();
        report.setStatus(ReportStatus.UNDER_REVIEW);
        reportRepository.save(report);

        audit("REPORT_UNDER_REVIEW", "REPORT", report.getId(), previous,
            ReportStatus.UNDER_REVIEW.name(), null);
        return ResponseEntity.ok(mapReport(report));
    }

    @PutMapping("/moderation-reports/{id}/resolve")
    public ResponseEntity<ReportDto> resolveReport(@PathVariable Long id,
                                                  @RequestParam String reason) {
        return closeReport(id, ReportStatus.RESOLVED, reason);
    }

    @PutMapping("/moderation-reports/{id}/dismiss")
    public ResponseEntity<ReportDto> dismissReport(@PathVariable Long id,
                                                  @RequestParam String reason) {
        return closeReport(id, ReportStatus.DISMISSED, reason);
    }

    /**
     * Resolving a report only closes the report. It never suspends the reported
     * account or deletes the reported listing; those stay separate, deliberate
     * administrator decisions made on the account or posting page.
     */
    private ResponseEntity<ReportDto> closeReport(Long id, ReportStatus outcome, String reason) {
        String checkedReason = requireReason(reason);
        Report report = requireReport(id);
        String previous = report.getStatus().name();
        User admin = currentAdmin();

        report.setStatus(outcome);
        report.setAdminNotes(checkedReason);
        report.setResolvedAt(LocalDateTime.now());
        report.setResolvedByAdminId(admin.getId());
        reportRepository.save(report);

        audit(outcome == ReportStatus.RESOLVED ? "REPORT_RESOLVED" : "REPORT_DISMISSED",
            "REPORT", report.getId(), previous, outcome.name(), checkedReason);

        if (report.getReporter() != null) {
            notificationService.notify(report.getReporter(), NotificationService.REPORT_RESOLVED,
                outcome == ReportStatus.RESOLVED ? "Report resolved" : "Report dismissed",
                "Your report was " + outcome.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + ". Note: " + checkedReason,
                "/job-seeker/reports.html", "REPORT", report.getId(),
                "report-" + report.getId() + "-" + outcome.name().toLowerCase(Locale.ROOT));
        }
        return ResponseEntity.ok(mapReport(report));
    }

    @GetMapping("/moderation-reports/stats")
    public ResponseEntity<ReportStatsDto> getReportStats() {
        ReportStatsDto stats = new ReportStatsDto();
        stats.setOpenReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.OPEN).size());
        stats.setUnderReviewReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.UNDER_REVIEW).size());
        stats.setResolvedReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.RESOLVED).size());
        stats.setDismissedReports(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.DISMISSED).size());
        stats.setFraudReports(reportRepository.findByCategory(ReportCategory.FRAUD_OR_SCAM).size());
        stats.setMisinformationReports(reportRepository.findByCategory(ReportCategory.MISLEADING_INFORMATION).size());
        stats.setDuplicateReports(reportRepository.findByCategory(ReportCategory.DUPLICATE_LISTING).size());
        return ResponseEntity.ok(stats);
    }

    /* =====================================================================
       Suspicious listings
       ===================================================================== */

    @GetMapping("/suspicious-listings")
    public ResponseEntity<List<SuspiciousListingDto>> getSuspiciousListings(
            @RequestParam(required = false) String rule,
            @RequestParam(required = false) String state) {
        List<SuspiciousListing> flags;
        if ("dismissed".equalsIgnoreCase(state)) {
            flags = suspiciousListingRepository.findByDismissedTrueOrderByFlaggedAtDesc();
        } else if ("open".equalsIgnoreCase(state)) {
            flags = suspiciousListingRepository.findActiveByFlaggedAtDesc();
        } else {
            flags = suspiciousListingRepository.findAllByOrderByFlaggedAtDesc();
        }
        if (rule != null && !rule.isBlank()) {
            flags = flags.stream()
                .filter(flag -> rule.equalsIgnoreCase(flag.getTriggeredRule()))
                .collect(Collectors.toList());
        }
        return ResponseEntity.ok(flags.stream().map(this::mapSuspiciousListing).collect(Collectors.toList()));
    }

    /**
     * Re-runs the deterministic checks over every posting still in play. It only
     * creates flags; nothing is rejected or suspended here.
     */
    @PostMapping("/suspicious-listings/detect")
    public ResponseEntity<Map<String, Object>> runSuspiciousDetection() {
        List<JobPost> posts = new ArrayList<>();
        posts.addAll(jobPostRepository.findByModerationStatus(ModerationStatus.PENDING_REVIEW));
        posts.addAll(jobPostRepository.findByModerationStatus(ModerationStatus.APPROVED));

        List<SuspiciousListing> created = new ArrayList<>();
        for (JobPost post : posts) {
            created.addAll(suspiciousListingService.evaluate(post));
        }
        suspiciousListingService.notifyAdmins(created, notificationService);

        audit("SUSPICIOUS_DETECTION_RUN", "SYSTEM", 0L, null, null,
            created.size() + " flag(s) created across " + posts.size() + " posting(s)");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postsScanned", posts.size());
        result.put("flagsCreated", created.size());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/suspicious-listings/{id}/dismiss")
    public ResponseEntity<SuspiciousListingDto> dismissSuspiciousListing(@PathVariable Long id,
                                                                        @RequestParam String reason) {
        String checkedReason = requireReason(reason);
        SuspiciousListing flag = suspiciousListingRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Flag not found"));

        flag.setDismissed(true);
        flag.setDismissReason(checkedReason);
        flag.setDismissedAt(LocalDateTime.now());
        suspiciousListingRepository.save(flag);

        audit("SUSPICIOUS_LISTING_DISMISSED", "SUSPICIOUS_LISTING", flag.getId(), null,
            "DISMISSED", checkedReason);
        return ResponseEntity.ok(mapSuspiciousListing(flag));
    }

    /* =====================================================================
       Account moderation
       ===================================================================== */

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream()
            .sorted(Comparator.comparing(User::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(this::mapUser)
            .collect(Collectors.toList()));
    }

    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendUser(@PathVariable Long id,
                                                          @RequestParam String reason) {
        String checkedReason = requireReason(reason);
        User target = requireUser(id);
        User admin = currentAdmin();

        if (target.getId().equals(admin.getId())) {
            throw new IllegalArgumentException("You cannot suspend your own administrator account.");
        }
        if (target.getUserType() == UserType.ADMIN
            && userRepository.countByUserTypeAndIsActiveTrue(UserType.ADMIN) <= 1) {
            throw new IllegalArgumentException("Cannot suspend the last active administrator.");
        }

        target.setActive(false);
        userRepository.save(target);

        audit("ACCOUNT_SUSPENDED", "USER", target.getId(), "ACTIVE", "SUSPENDED", checkedReason);
        notificationService.notify(target, NotificationService.ACCOUNT_SUSPENDED,
            "Account suspended",
            "Your account has been suspended. Reason: " + checkedReason,
            "/login.html", "USER", target.getId(),
            "account-suspended-" + target.getId() + "-" + System.currentTimeMillis());

        return ResponseEntity.ok(mapUser(target));
    }

    @PutMapping("/users/{id}/restore")
    public ResponseEntity<Map<String, Object>> restoreUser(@PathVariable Long id) {
        User target = requireUser(id);

        target.setActive(true);
        userRepository.save(target);

        audit("ACCOUNT_RESTORED", "USER", target.getId(), "SUSPENDED", "ACTIVE", null);
        notificationService.notify(target, NotificationService.ACCOUNT_RESTORED,
            "Account restored",
            "Your account has been restored. You can sign in again.",
            "/login.html", "USER", target.getId(),
            "account-restored-" + target.getId() + "-" + System.currentTimeMillis());

        return ResponseEntity.ok(mapUser(target));
    }

    /* =====================================================================
       Audit log (append-only; there is deliberately no edit or delete route)
       ===================================================================== */

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogDto>> getAuditLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String adminEmail,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<AuditLog> logs;
        if (from != null || to != null) {
            LocalDateTime start = from != null
                ? LocalDate.parse(from).atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
            LocalDateTime end = to != null
                ? LocalDate.parse(to).plusDays(1).atStartOfDay()
                : LocalDateTime.now().plusDays(1);
            logs = auditLogRepository.findByCreatedAtBetween(start, end);
        } else if (actionType != null && !actionType.isBlank()) {
            logs = auditLogRepository.findByActionTypeContainingOrderByCreatedAtDesc(actionType.toUpperCase(Locale.ROOT));
        } else if (targetId != null) {
            logs = auditLogRepository.findByTargetIdOrderByCreatedAtDesc(targetId);
        } else {
            logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        }

        List<AuditLogDto> dtos = logs.stream()
            .filter(log -> adminEmail == null || adminEmail.isBlank()
                || (log.getAdminUser() != null && log.getAdminUser().getEmail() != null
                    && log.getAdminUser().getEmail().toLowerCase(Locale.ROOT)
                        .contains(adminEmail.toLowerCase(Locale.ROOT))))
            .filter(log -> targetType == null || targetType.isBlank()
                || targetType.equalsIgnoreCase(log.getTargetType()))
            .sorted(Comparator.comparing(AuditLog::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(this::mapAuditLog)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /* =====================================================================
       Helpers
       ===================================================================== */

    /** The signed-in administrator. Never supplied by the client. */
    private User currentAdmin() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("No authenticated administrator");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new AccessDeniedException("Administrator account not found"));
    }

    private void audit(String actionType, String targetType, Long targetId,
                       String previousState, String newState, String reason) {
        User admin = currentAdmin();
        auditLogRepository.save(AuditLog.builder()
            .actionType(actionType)
            .adminUser(admin)
            .targetType(targetType)
            .targetId(targetId)
            .previousState(previousState)
            .newState(newState)
            .reason(reason)
            .build());
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An explanation is required for this action.");
        }
        return reason.trim();
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private EmployerProfile requireEmployerProfile(Long idOrUserId) {
        return employerRepository.findById(idOrUserId)
            .orElseGet(() -> employerRepository.findByUserId(idOrUserId)
                .orElseThrow(() -> new IllegalArgumentException("Employer not found")));
    }

    private JobPost requireJobPost(Long id) {
        return jobPostRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Job post not found"));
    }

    private Report requireReport(Long id) {
        return reportRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Report not found"));
    }

    private AdminEmployerDto mapEmployer(EmployerProfile employer) {
        AdminEmployerDto dto = new AdminEmployerDto();
        User account = employer.getUser();
        if (account != null) {
            dto.setUserId(account.getId());
            dto.setEmail(account.getEmail());
            dto.setVerified(account.isVerified());
            dto.setActive(account.isActive());
        }
        dto.setVerificationStatus(employer.getVerificationStatus());
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
        dto.setJobPostCount(jobPostRepository.countByEmployerId(employer.getId()));
        return dto;
    }

    private AdminJobPostDto mapJobPost(JobPost job) {
        AdminJobPostDto dto = new AdminJobPostDto();
        dto.setId(job.getId());
        dto.setJobTitle(job.getJobTitle());
        dto.setCategory(job.getJobCategory());
        dto.setEmploymentType(job.getEmploymentType());
        dto.setMunicipality(job.getMunicipality());
        dto.setBarangay(job.getBarangay());
        dto.setJobLocation(job.getJobLocation());
        dto.setJobDescription(job.getJobDescription());
        dto.setResponsibilities(job.getResponsibilities());
        dto.setRequiredSkills(job.getRequiredSkills());
        dto.setSalaryMin(job.getSalaryMin());
        dto.setSalaryMax(job.getSalaryMax());
        dto.setAvailablePositions(job.getAvailablePositions());
        dto.setActive(job.isActive());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());
        dto.setModerationStatus(job.getModerationStatus());
        dto.setApplicantCount(applicationRepository.findByJobPostId(job.getId()).size());

        EmployerProfile employer = job.getEmployer();
        if (employer != null) {
            dto.setEmployerName(employer.getBusinessName());
            dto.setEmployerVerificationStatus(employer.getVerificationStatus());
            if (employer.getUser() != null) {
                dto.setEmployerUserId(employer.getUser().getId());
            }
        }
        return dto;
    }

    private Map<String, Object> mapUser(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("email", user.getEmail());
        row.put("userType", user.getUserType());
        row.put("active", user.isActive());
        row.put("verified", user.isVerified());
        row.put("createdAt", user.getCreatedAt());

        if (user.getUserType() == UserType.EMPLOYER) {
            employerRepository.findByUserId(user.getId()).ifPresent(profile -> {
                row.put("businessName", profile.getBusinessName());
                row.put("employerName", profile.getEmployerName());
                row.put("verificationStatus", profile.getVerificationStatus());
                row.put("municipality", profile.getMunicipality());
            });
        } else if (user.getUserType() == UserType.JOB_SEEKER) {
            seekerRepository.findByUserId(user.getId()).ifPresent(profile -> {
                row.put("fullName", profile.getFullName());
                row.put("municipality", profile.getMunicipality());
            });
        }
        return row;
    }

    private ReportDto mapReport(Report report) {
        ReportDto dto = new ReportDto();
        dto.setId(report.getId());
        dto.setReporterEmail(report.getReporter() != null ? report.getReporter().getEmail() : null);
        dto.setReportedTargetType(report.getReportedTargetType());
        dto.setReportedTargetId(report.getReportedTargetId());
        dto.setCategory(report.getCategory());
        dto.setDescription(report.getDescription());
        dto.setStatus(report.getStatus());
        dto.setAdminNotes(report.getAdminNotes());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setResolvedAt(report.getResolvedAt());
        dto.setIsDuplicate(report.isDuplicate());
        return dto;
    }

    private AuditLogDto mapAuditLog(AuditLog log) {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(log.getId());
        dto.setActionType(log.getActionType());
        dto.setAdminUserEmail(log.getAdminUser() != null ? log.getAdminUser().getEmail() : null);
        dto.setTargetType(log.getTargetType());
        dto.setTargetId(log.getTargetId());
        dto.setReason(log.getReason());
        dto.setPreviousState(log.getPreviousState());
        dto.setNewState(log.getNewState());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private SuspiciousListingDto mapSuspiciousListing(SuspiciousListing flag) {
        SuspiciousListingDto dto = new SuspiciousListingDto();
        dto.setId(flag.getId());
        JobPost job = flag.getJobPost();
        if (job != null) {
            dto.setJobPostId(job.getId());
            dto.setJobTitle(job.getJobTitle());
            if (job.getEmployer() != null) {
                dto.setEmployerName(job.getEmployer().getBusinessName());
            }
        }
        dto.setFlaggedReason(flag.getFlaggedReason());
        dto.setTriggeredRule(flag.getTriggeredRule());
        dto.setStatus(flag.getStatus());
        dto.setFlaggedAt(flag.getFlaggedAt());
        dto.setDismissed(flag.isDismissed());
        dto.setDismissReason(flag.getDismissReason());
        dto.setDismissedAt(flag.getDismissedAt());
        return dto;
    }
}
