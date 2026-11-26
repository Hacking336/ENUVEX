package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.*;
import com.jobskills.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployerAnalyticsService {

    private final JobPostRepository jobPostRepository;
    private final JobApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final EmployerProfileRepository employerRepository;
    private final UserRepository userRepository;
    private final MatchingEngine matchingEngine;

    // ============================================================================
    // Helper: parse date range from string parameter
    // ============================================================================

    /** Parse a dateRange string like "all-time", "90d", "30d", "7d", "this-month", "prev-month" */
    private static LocalDateTime[] parseDateRange(String dateRange) {
        if (dateRange == null || dateRange.isBlank() || "all-time".equalsIgnoreCase(dateRange)) {
            return new LocalDateTime[]{null, null};
        }
        LocalDateTime now = LocalDateTime.now();
        // Use end of day for the "to" bound so the full day is included
        LocalDateTime to = now.withHour(23).withMinute(59).withSecond(59).withNano(0);
        LocalDateTime from;
        switch (dateRange.toLowerCase()) {
            case "90d":
                from = now.minusDays(90);
                return new LocalDateTime[]{from, to};
            case "30d":
                from = now.minusDays(30);
                return new LocalDateTime[]{from, to};
            case "7d":
                from = now.minusDays(7);
                return new LocalDateTime[]{from, to};
            case "this-month":
                from = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                return new LocalDateTime[]{from, to};
            case "prev-month":
                // Previous month: from first day of previous month to last day of previous month
                LocalDateTime firstThisMonth = now.withDayOfMonth(1);
                LocalDateTime lastThisMonth = now.withDayOfMonth(1).minusDays(1);
                from = lastThisMonth.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                to = lastThisMonth.withHour(23).withMinute(59).withSecond(59).withNano(0);
                return new LocalDateTime[]{from, to};
            default:
                // Unknown filter, default to all-time
                return new LocalDateTime[]{null, null};
        }
    }

    /** Check if appliedAt falls within the given inclusive date window */
    private static boolean isWithinDateRange(LocalDateTime appliedAt, LocalDateTime from, LocalDateTime to) {
        if (appliedAt == null) return false;
        if (from != null && appliedAt.isBefore(from)) return false;
        if (to != null && appliedAt.isAfter(to)) return false;
        return true;
    }

    // ============================================================================
    // Overview Cards
    // ============================================================================

    @Transactional(readOnly = true)
    public EmployerAnalyticsOverviewDto overview(Long employerId, String dateRange) {
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        EmployerProfile employer = employerRepository.findById(employerId)
            .orElseThrow(() -> new IllegalArgumentException("Employer not found"));

        // Get jobs for this employer
        List<JobPost> jobs = jobPostRepository.findByEmployerId(employerId);
        List<Long> jobIds = jobs.stream().map(JobPost::getId).collect(Collectors.toList());

        long totalJobPosts = jobs.size();

        // Get all applications for these jobs, filtered by date
        long totalApplicants = 0;
        List<JobApplication> allApps = new ArrayList<>();
        if (!jobIds.isEmpty()) {
            // Fetch applications per job post (repository has findByJobPostId, not findByJobPostIdIn)
            for (Long jobId : jobIds) {
                allApps.addAll(applicationRepository.findByJobPostId(jobId));
            }
            // Remove duplicates and filter by date
            Set<Long> uniqueAppIds = allApps.stream().map(JobApplication::getId).collect(Collectors.toSet());
            allApps = allApps.stream().filter(app -> uniqueAppIds.contains(app.getId())).collect(Collectors.toList());
            // Filter by appliedAt date
            allApps = allApps.stream()
                .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                .collect(Collectors.toList());
            totalApplicants = allApps.size();
        }

        // Applicants by current status - count from applications (within date range)
        long underReview = 0, shortlisted = 0, interviewed = 0, accepted = 0, rejected = 0;
        if (!allApps.isEmpty()) {
            for (JobApplication app : allApps) {
                ApplicationStatus appStatus = app.getApplicationStatus();
                switch (appStatus) {
                    case REVIEWING -> underReview++;
                    case SHORTLISTED -> shortlisted++;
                    case INTERVIEW -> interviewed++;
                    case ACCEPTED -> accepted++;
                    case REJECTED -> rejected++;
                }
            }
        }

        // New applicants: applications with status SUBMITTED (recently applied, within date range)
        long newApplicants = 0;
        if (!allApps.isEmpty()) {
            for (JobApplication app : allApps) {
                if (app.getApplicationStatus() == ApplicationStatus.SUBMITTED) newApplicants++;
            }
        }

        // Upcoming interviews - applications currently in INTERVIEW status (within date range)
        long upcomingInterviews = 0;
        if (!allApps.isEmpty()) {
            for (JobApplication app : allApps) {
                if (app.getApplicationStatus() == ApplicationStatus.INTERVIEW) upcomingInterviews++;
            }
        }

        EmployerAnalyticsOverviewDto dto = new EmployerAnalyticsOverviewDto();
        dto.setTotalJobPosts(totalJobPosts);
        dto.setActiveJobPosts(totalJobPosts);
        dto.setTotalApplicants(totalApplicants);
        dto.setNewApplicants(newApplicants);
        dto.setApplicantsUnderReview(underReview);
        dto.setShortlistedApplicants(shortlisted);
        dto.setInterviewedApplicants(interviewed);
        dto.setAcceptedApplicants(accepted);
        dto.setRejectedApplicants(rejected);
        dto.setUpcomingInterviews(upcomingInterviews);
        return dto;
    }

    // ============================================================================
    // Job Posting Performance
    // ============================================================================

    @Transactional(readOnly = true)
    public Page<EmployerJobPerformanceDto> jobPerformance(
            Long employerId, String sortBy, String sortDir,
            String municipality, EmploymentType employmentType,
            String statusFilter, Integer page, Integer size,
            String dateRange) {

        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        // Only get jobs for this employer
        List<JobPost> allJobs = jobPostRepository.findByEmployerId(employerId);

        // Filter by date: only include jobs with applications within date range
        // We'll filter applications per-job later, but for the job list we keep all jobs
        // that have at least one application within the date range, or all jobs if no filter

        // Filter by municipality if specified
        if (municipality != null && !municipality.isEmpty()) {
            Municipality mun = Municipality.valueOf(municipality.toUpperCase());
            allJobs = allJobs.stream()
                .filter(j -> j.getMunicipality() != null && j.getMunicipality() == mun)
                .collect(Collectors.toList());
        }

        // Filter by employment type if specified
        if (employmentType != null && employmentType.name() != null && !employmentType.name().isEmpty()) {
            EmploymentType et = EmploymentType.valueOf(employmentType.name());
            allJobs = allJobs.stream()
                .filter(j -> j.getEmploymentType() != null && j.getEmploymentType() == et)
                .collect(Collectors.toList());
        }

        // Filter by status if specified
        if (statusFilter != null && !statusFilter.isEmpty()) {
            allJobs = allJobs.stream()
                .filter(j -> j.isActive() ? "Active".equals(statusFilter) : "Inactive".equals(statusFilter))
                .collect(Collectors.toList());
        }

        // Sort. sortDir and sortBy are optional query parameters, so neither may be
        // dereferenced directly; unknown sort keys fall back to the creation date.
        boolean ascending = "asc".equalsIgnoreCase(sortDir);
        Comparator<JobPost> order = switch (sortBy == null ? "" : sortBy) {
            case "jobTitle" -> Comparator.comparing(JobPost::getJobTitle,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "municipality" -> Comparator.comparing(JobPost::getMunicipality,
                Comparator.nullsLast(Comparator.naturalOrder()));
            case "employmentType" -> Comparator.comparing(JobPost::getEmploymentType,
                Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(JobPost::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        };
        allJobs = allJobs.stream()
            .sorted(ascending ? order : order.reversed())
            .collect(Collectors.toList());
        int start = page * size;
        int end = Math.min(start + size, allJobs.size());
        if (start > allJobs.size()) start = allJobs.size();
        if (end > allJobs.size()) end = allJobs.size();

        List<JobPost> pagedJobs = allJobs.subList(start, end);

        // For each job, compute performance stats with date filtering
        List<EmployerJobPerformanceDto> dtos = pagedJobs.stream().map(job -> {
            List<JobApplication> applications = applicationRepository.findByJobPostId(job.getId());

            // Filter by appliedAt date range
            if (dateFrom != null || dateTo != null) {
                applications = applications.stream()
                    .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                    .collect(Collectors.toList());
            }

            long total = applications.size();
            long newApps = 0, underReview = 0, shortlisted = 0, interviewed = 0, accepted = 0, rejected = 0;

            for (JobApplication app : applications) {
                ApplicationStatus appStatus = app.getApplicationStatus();
                switch (appStatus) {
                    case SUBMITTED -> newApps++;
                    case REVIEWING -> underReview++;
                    case SHORTLISTED -> shortlisted++;
                    case INTERVIEW -> interviewed++;
                    case ACCEPTED -> accepted++;
                    case REJECTED -> rejected++;
                }
            }

            // Average match score
            double avgMatch = 0.0;
            if (total > 0) {
                double sum = applications.stream()
                    .mapToDouble(app -> app.getMatchPercentage() != null ? app.getMatchPercentage().doubleValue() : 0.0)
                    .sum();
                avgMatch = sum / total;
            }

            EmployerJobPerformanceDto dto = new EmployerJobPerformanceDto();
            dto.setId(job.getId());
            dto.setJobTitle(job.getJobTitle());
            dto.setMunicipality(job.getMunicipality() != null ? job.getMunicipality().name() : null);
            dto.setBarangay(job.getBarangay());
            dto.setEmploymentType(job.getEmploymentType() != null ? job.getEmploymentType().name() : null);
            dto.setDatePosted(job.getCreatedAt());
            dto.setStatus(job.isActive() ? "Active" : "Inactive");
            dto.setTotalApplicants(total);
            dto.setNewApplicants(newApps);
            dto.setShortlisted(shortlisted);
            dto.setInterviewed(interviewed);
            dto.setAccepted(accepted);
            dto.setRejected(rejected);
            dto.setAverageMatchScore(avgMatch);
            dto.setActions("View Job, View Applicants, View Analytics");
            return dto;
        }).collect(Collectors.toList());

        // Return a simple page (since we already paginated manually)
        return new PageImpl<>(dtos, PageRequest.of(page, size), allJobs.size());
    }

    // ============================================================================
    // Applicant Funnel
    // ============================================================================

    @Transactional(readOnly = true)
    public EmployerApplicantFunnelDto funnel(Long employerId, String dateRange) {
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        List<Long> jobIds = jobPostRepository.findByEmployerId(employerId).stream()
            .map(JobPost::getId).collect(Collectors.toList());

        if (jobIds.isEmpty()) {
            return new EmployerApplicantFunnelDto();
        }

        // Get all applications for these jobs
        List<JobApplication> allApps = new ArrayList<>();
        for (Long jobId : jobIds) {
            allApps.addAll(applicationRepository.findByJobPostId(jobId));
        }

        // Remove duplicates (same applicant can apply to same job only once, but could apply to multiple jobs)
        // We count unique application records
        Set<Long> uniqueAppIds = allApps.stream().map(JobApplication::getId).collect(Collectors.toSet());
        allApps = allApps.stream().filter(app -> uniqueAppIds.contains(app.getId())).collect(Collectors.toList());

        // Filter by appliedAt date range
        if (dateFrom != null || dateTo != null) {
            allApps = allApps.stream()
                .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                .collect(Collectors.toList());
        }

        long applicationsReceived = allApps.size();

        // Stage counts from current status (within date range)
        long reviewing = 0, shortlisted = 0, interviewed = 0, accepted = 0, rejected = 0;
        for (JobApplication app : allApps) {
            ApplicationStatus status = app.getApplicationStatus();
            switch (status) {
                case REVIEWING -> reviewing++;
                case SHORTLISTED -> shortlisted++;
                case INTERVIEW -> interviewed++;
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
            }
        }

        // Calculate conversion rates
        long[] stageCounts = {applicationsReceived, reviewing, shortlisted, interviewed, accepted, rejected};
        double[] conversionRates = new double[stageCounts.length - 1];
        for (int i = 0; i < conversionRates.length; i++) {
            long denominator = (i == 0) ? applicationsReceived : stageCounts[i - 1];
            if (denominator > 0) {
                conversionRates[i] = (double) stageCounts[i] / denominator * 100.0;
            } else {
                conversionRates[i] = 0.0;
            }
        }

        // Calculate drop-off between stages
        long[] dropOff = new long[stageCounts.length - 1];
        for (int i = 0; i < dropOff.length; i++) {
            if (i == 0) {
                dropOff[i] = applicationsReceived - stageCounts[i];
            } else {
                dropOff[i] = stageCounts[i - 1] - stageCounts[i];
            }
        }

        EmployerApplicantFunnelDto dto = new EmployerApplicantFunnelDto();
        dto.setApplicationsReceived(applicationsReceived);
        dto.setReviewing(reviewing);
        dto.setShortlisted(shortlisted);
        dto.setInterviewed(interviewed);
        dto.setAccepted(accepted);
        dto.setRejected(rejected);
        dto.setConversionRates(conversionRates);
        dto.setDropOff(dropOff);
        return dto;
    }

    // ============================================================================
    // Application Response Metrics
    // ============================================================================

    @Transactional(readOnly = true)
    public EmployerResponseMetricsDto responseMetrics(Long employerId, String dateRange) {
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        List<Long> jobIds = jobPostRepository.findByEmployerId(employerId).stream()
            .map(JobPost::getId).collect(Collectors.toList());

        if (jobIds.isEmpty()) {
            return new EmployerResponseMetricsDto();
        }

        // Get all applications for these jobs
        List<JobApplication> allApps = new ArrayList<>();
        for (Long jobId : jobIds) {
            allApps.addAll(applicationRepository.findByJobPostId(jobId));
        }

        // Remove duplicates
        Set<Long> uniqueAppIds = allApps.stream().map(JobApplication::getId).collect(Collectors.toSet());
        allApps = allApps.stream().filter(app -> uniqueAppIds.contains(app.getId())).collect(Collectors.toList());

        // Filter by appliedAt date range
        if (dateFrom != null || dateTo != null) {
            allApps = allApps.stream()
                .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                .collect(Collectors.toList());
        }

        // We need status history to compute time-based metrics
        Map<Long, List<ApplicationStatusHistory>> historyByApp = new HashMap<>();
        for (JobApplication app : allApps) {
            List<ApplicationStatusHistory> history = historyRepository.findByApplicationIdOrderByCreatedAtAscIdAsc(app.getId());
            historyByApp.put(app.getId(), history);
        }

        // Collect time differences
        List<Long> timesToReview = new ArrayList<>();
        List<Long> timesToShortlist = new ArrayList<>();
        List<Long> timesToInterview = new ArrayList<>();
        List<Long> timesToDecision = new ArrayList<>();

        for (JobApplication app : allApps) {
            LocalDateTime appliedAt = app.getAppliedAt();
            if (appliedAt == null) continue;

            // Only include if within date range
            if (!isWithinDateRange(appliedAt, dateFrom, dateTo)) continue;

            List<ApplicationStatusHistory> history = historyByApp.getOrDefault(app.getId(), List.of());

            // Find first REVIEWING
            Optional<ApplicationStatusHistory> firstReviewing = history.stream()
                .filter(h -> h.getNewStatus() == ApplicationStatus.REVIEWING)
                .findFirst();

            // Find first SHORTLISTED
            Optional<ApplicationStatusHistory> firstShortlisted = history.stream()
                .filter(h -> h.getNewStatus() == ApplicationStatus.SHORTLISTED)
                .findFirst();

            // Find first INTERVIEW
            Optional<ApplicationStatusHistory> firstInterview = history.stream()
                .filter(h -> h.getNewStatus() == ApplicationStatus.INTERVIEW)
                .findFirst();

            // Find first ACCEPTED or REJECTED (decision)
            Optional<ApplicationStatusHistory> firstDecision = history.stream()
                .filter(h -> h.getNewStatus() == ApplicationStatus.ACCEPTED
                    || h.getNewStatus() == ApplicationStatus.REJECTED)
                .findFirst();

            if (firstReviewing.isPresent()) {
                long minutes = minutesBetween(appliedAt, firstReviewing.get().getCreatedAt());
                timesToReview.add(minutes);
            }
            if (firstShortlisted.isPresent()) {
                long minutes = minutesBetween(appliedAt, firstShortlisted.get().getCreatedAt());
                timesToShortlist.add(minutes);
            }
            if (firstInterview.isPresent()) {
                long minutes = minutesBetween(appliedAt, firstInterview.get().getCreatedAt());
                timesToInterview.add(minutes);
            }
            if (firstDecision.isPresent()) {
                long minutes = minutesBetween(appliedAt, firstDecision.get().getCreatedAt());
                timesToDecision.add(minutes);
            }
        }

        EmployerResponseMetricsDto dto = new EmployerResponseMetricsDto();

        // Time to first review - average and median
        if (!timesToReview.isEmpty()) {
            timesToReview.sort(Long::compareTo);
            long sum = timesToReview.stream().mapToLong(Long::intValue).sum();
            dto.setTimeToReviewAvg((double) sum / timesToReview.size());
            dto.setTimeToReviewMedian((double) timesToReview.get(timesToReview.size() / 2));
            dto.setTimeToReviewAvailable(true);
        }

        // Time to shortlist
        if (!timesToShortlist.isEmpty()) {
            timesToShortlist.sort(Long::compareTo);
            long sum = timesToShortlist.stream().mapToLong(Long::intValue).sum();
            dto.setTimeToShortlistAvg((double) sum / timesToShortlist.size());
            dto.setTimeToShortlistMedian((double) timesToShortlist.get(timesToShortlist.size() / 2));
            dto.setTimeToShortlistAvailable(true);
        }

        // Time to interview
        if (!timesToInterview.isEmpty()) {
            timesToInterview.sort(Long::compareTo);
            long sum = timesToInterview.stream().mapToLong(Long::intValue).sum();
            dto.setTimeToInterviewAvg((double) sum / timesToInterview.size());
            dto.setTimeToInterviewMedian((double) timesToInterview.get(timesToInterview.size() / 2));
            dto.setTimeToInterviewAvailable(true);
        }

        // Time to decision (accepted or rejected)
        if (!timesToDecision.isEmpty()) {
            timesToDecision.sort(Long::compareTo);
            long sum = timesToDecision.stream().mapToLong(Long::intValue).sum();
            dto.setTimeToDecisionAvg((double) sum / timesToDecision.size());
            dto.setTimeToDecisionMedian((double) timesToDecision.get(timesToDecision.size() / 2));
            dto.setTimeToDecisionAvailable(true);
        }

        return dto;
    }

    private long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return 0L;
        long minutes = ChronoUnit.MINUTES.between(from, to);
        return minutes < 0 ? 0 : minutes;
    }

    // ============================================================================
    // Matching Insights
    // ============================================================================

    @Transactional(readOnly = true)
    public EmployerMatchingInsightsDto matchingInsights(Long employerId, String dateRange) {
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        List<Long> jobIds = jobPostRepository.findByEmployerId(employerId).stream()
            .map(JobPost::getId).collect(Collectors.toList());

        if (jobIds.isEmpty()) {
            return new EmployerMatchingInsightsDto();
        }

        // Get all applications for these jobs
        List<JobApplication> allApps = new ArrayList<>();
        for (Long jobId : jobIds) {
            allApps.addAll(applicationRepository.findByJobPostId(jobId));
        }

        // Remove duplicates
        Set<Long> uniqueAppIds = allApps.stream().map(JobApplication::getId).collect(Collectors.toSet());
        allApps = allApps.stream().filter(app -> uniqueAppIds.contains(app.getId())).collect(Collectors.toList());

        // Filter by appliedAt date range
        if (dateFrom != null || dateTo != null) {
            allApps = allApps.stream()
                .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                .collect(Collectors.toList());
        }

        // Calculate match statistics from stored matchPercentage
        List<Long> matchScores = new ArrayList<>();
        long highestMatch = 0;
        long excellentMatches = 0; // 90%+
        long strongMatches = 0;    // 75-89%
        long goodMatches = 0;      // 60-74%
        long partialMatches = 0;   // 40-59%
        long lowMatches = 0;       // < 40%

        for (JobApplication app : allApps) {
            if (app.getMatchPercentage() != null) {
                matchScores.add(app.getMatchPercentage().longValue());

                // Categorize by score ranges
                long score = app.getMatchPercentage().longValue();
                if (score >= 90) excellentMatches++;
                else if (score >= 75) strongMatches++;
                else if (score >= 60) goodMatches++;
                else if (score >= 40) partialMatches++;
                else lowMatches++;

                // Track highest
                if (score > highestMatch) {
                    highestMatch = score;
                }
            }
        }

        // Score distribution
        Map<Integer, Long> scoreDistribution = new TreeMap<>();
        if (!matchScores.isEmpty()) {
            for (Long scoreLong : matchScores) {
                int score = scoreLong.intValue();
                scoreDistribution.merge(score, 1L, Long::sum);
            }
        }

        EmployerMatchingInsightsDto dto = new EmployerMatchingInsightsDto();
        dto.setAverageMatchScore(matchScores.isEmpty() ? 0.0
            : matchScores.stream().mapToDouble(Long::doubleValue).average().orElse(0.0));
        dto.setHighestMatch(highestMatch / 100.0);
        dto.setExcellentMatches(excellentMatches);
        dto.setStrongMatches(strongMatches);
        dto.setGoodMatches(goodMatches);
        dto.setPartialMatches(partialMatches);
        dto.setLowMatches(lowMatches);
        dto.setScoreDistribution(scoreDistribution);
        return dto;
    }

    // ============================================================================
    // Skills Analysis per Job Posting
    // ============================================================================

    @Transactional(readOnly = true)
    public EmployerSkillsAnalysisDto skillsAnalysis(Long employerId, Long jobId, String dateRange) {
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        requireJobOwner(job, employerId);

        List<JobApplication> applications = applicationRepository.findByJobPostId(jobId);
        String requiredSkills = job.getRequiredSkills();

        // Parse required skills
        List<String> requiredSkillList = parseSkills(requiredSkills);

        EmployerSkillsAnalysisDto dto = new EmployerSkillsAnalysisDto();
        dto.setJobTitle(job.getJobTitle());
        dto.setRequiredSkills(requiredSkills);
        dto.setApplicantCount(applications.size());

        if (requiredSkillList.isEmpty()) {
            // No required skills defined for this job
            dto.setMostCommonApplicantSkills(new ArrayList<>());
            dto.setMissingRequiredSkills(new ArrayList<>());
            dto.setSkillsCoverage(new ArrayList<>());
            return dto;
        }

        // Filter by appliedAt date range
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];
        if (dateFrom != null || dateTo != null) {
            applications = applications.stream()
                .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                .collect(Collectors.toList());
        }

        // For each required skill, compute what percentage of applicants have it
        List<SkillCoverageDto> coverageList = new ArrayList<>();

        for (String requiredSkill : requiredSkillList) {
            long applicantsWithSkill = 0;
            for (JobApplication app : applications) {
                JobSeekerProfile seeker = app.getJobSeeker();
                if (seeker != null && hasSkill(seeker.getSkills(), requiredSkill)) {
                    applicantsWithSkill++;
                }
            }

            double coveragePct = applications.isEmpty() ? 0.0
                : (double) applicantsWithSkill / applications.size() * 100.0;

            SkillCoverageDto coverage = new SkillCoverageDto();
            coverage.setSkill(requiredSkill);
            coverage.setApplicantsWithApplicantsWithSkill(applicantsWithSkill);
            coverage.setTotalApplicants(applications.size());
            coverage.setCoveragePercentage(coveragePct);
            coverage.setSampleSizeNote(applications.size() + " applicant" + (applications.size() != 1 ? "s" : ""));
            coverageList.add(coverage);
        }

        // Most common applicant skills (skills that applicants possess, not required)
        Map<String, Long> applicantSkillsFreq = new HashMap<>();
        for (JobApplication app : applications) {
            JobSeekerProfile seeker = app.getJobSeeker();
            if (seeker != null && seeker.getSkills() != null) {
                for (String skill : parseSkills(seeker.getSkills())) {
                    // Normalize and count, but exclude required skills
                    String normalized = normalizeSkill(skill);
                    if (!requiredSkillList.contains(normalized)) {
                        applicantSkillsFreq.merge(normalized, 1L, Long::sum);
                    }
                }
            }
        }

        // Top 10 most common applicant skills
        List<Map.Entry<String, Long>> topApplicantSkills = applicantSkillsFreq.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());

        dto.setMostCommonApplicantSkills(topApplicantSkills);
        dto.setMissingRequiredSkills(coverageList);
        dto.setSkillsCoverage(coverageList);
        return dto;
    }

    // ============================================================================
    // Geographic Applicant Insights
    // ============================================================================

    @Transactional(readOnly = true)
    public EmployerGeographicInsightsDto geographicInsights(Long employerId, String dateRange) {
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        List<Long> jobIds = jobPostRepository.findByEmployerId(employerId).stream()
            .map(JobPost::getId).collect(Collectors.toList());

        if (jobIds.isEmpty()) {
            return new EmployerGeographicInsightsDto();
        }

        // Get all applications for these jobs
        List<JobApplication> allApps = new ArrayList<>();
        for (Long jobId : jobIds) {
            allApps.addAll(applicationRepository.findByJobPostId(jobId));
        }

        // Remove duplicates
        Set<Long> uniqueAppIds = allApps.stream().map(JobApplication::getId).collect(Collectors.toSet());
        allApps = allApps.stream().filter(app -> uniqueAppIds.contains(app.getId())).collect(Collectors.toList());

        // Filter by appliedAt date range
        if (dateFrom != null || dateTo != null) {
            allApps = allApps.stream()
                .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                .collect(Collectors.toList());
        }

        // Applicant distribution by municipality
        Map<String, Long> byMunicipality = allApps.stream()
            .filter(app -> app.getJobSeeker() != null && app.getJobSeeker().getMunicipality() != null)
            .collect(Collectors.groupingBy(
                app -> app.getJobSeeker().getMunicipality().name(),
                Collectors.counting()
            ));

        // Applicant distribution by barangay
        Map<String, Long> byBarangay = allApps.stream()
            .filter(app -> app.getJobSeeker() != null && app.getJobSeeker().getBarangay() != null)
            .collect(Collectors.groupingBy(
                app -> app.getJobSeeker().getBarangay(),
                Collectors.counting()
            ));

        // Average match score by municipality
        Map<String, Double> avgMatchByMuni = new LinkedHashMap<>();
        for (Municipality m : Municipality.values()) {
            List<JobApplication> mApps = allApps.stream()
                .filter(app -> app.getJobSeeker() != null
                    && app.getJobSeeker().getMunicipality() != null
                    && app.getJobSeeker().getMunicipality() == m)
                .collect(Collectors.toList());

            if (!mApps.isEmpty()) {
                double sum = mApps.stream()
                    .mapToDouble(app -> app.getMatchPercentage() != null ? app.getMatchPercentage().doubleValue() : 0.0)
                    .sum();
                avgMatchByMuni.put(m.name(), sum / mApps.size());
            }
        }

        // Applicants by employment preference from the job posts
        Map<String, Long> byEmploymentType = allApps.stream()
            .filter(app -> app.getJobPost() != null && app.getJobPost().getEmploymentType() != null)
            .collect(Collectors.groupingBy(
                app -> app.getJobPost().getEmploymentType().name(),
                Collectors.counting()
            ));

        EmployerGeographicInsightsDto dto = new EmployerGeographicInsightsDto();
        dto.setApplicantsByMunicipality(byMunicipality);
        dto.setApplicantsByBarangay(byBarangay);
        dto.setAverageMatchScoreByMunicipality(avgMatchByMuni);
        dto.setApplicantsByEmploymentType(byEmploymentType);
        return dto;
    }

    // ============================================================================
    // Recruitment Insights (rule-based, from actual data)
    // ============================================================================

    @Transactional(readOnly = true)
    public List<EmployerInsightDto> insights(Long employerId, String dateRange) {
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        List<Long> jobIds = jobPostRepository.findByEmployerId(employerId).stream()
            .map(JobPost::getId).collect(Collectors.toList());

        if (jobIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all applications for these jobs
        List<JobApplication> allApps = new ArrayList<>();
        for (Long jobId : jobIds) {
            allApps.addAll(applicationRepository.findByJobPostId(jobId));
        }

        // Remove duplicates
        Set<Long> uniqueAppIds = allApps.stream().map(JobApplication::getId).collect(Collectors.toSet());
        allApps = allApps.stream().filter(app -> uniqueAppIds.contains(app.getId())).collect(Collectors.toList());

        // Filter by appliedAt date range
        if (dateFrom != null || dateTo != null) {
            allApps = allApps.stream()
                .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                .collect(Collectors.toList());
        }

        List<EmployerInsightDto> result = new ArrayList<>();

        // Insight 1: No applications
        if (allApps.isEmpty()) {
            EmployerInsightDto insight = new EmployerInsightDto();
            insight.setTitle("No applications");
            insight.setDescription("Your job posting(s) have not received any applicants.");
            insight.setPriority("info");
            result.add(insight);
        }

        // Insight 2: Skills gap - applicants missing required skills
        Map<Long, String> jobRequiredSkills = new HashMap<>();
        for (Long jobId : jobIds) {
            JobPost job = jobPostRepository.findById(jobId).orElse(null);
            if (job != null && job.getRequiredSkills() != null && !job.getRequiredSkills().isBlank()) {
                jobRequiredSkills.put(jobId, job.getRequiredSkills());
            }
        }

        for (Map.Entry<Long, String> entry : jobRequiredSkills.entrySet()) {
            List<JobApplication> jobApps = applicationRepository.findByJobPostId(entry.getKey());
            // Apply date filter to job apps
            if (dateFrom != null || dateTo != null) {
                jobApps = jobApps.stream()
                    .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                    .collect(Collectors.toList());
            }
            if (jobApps.isEmpty()) continue;

            String[] required = entry.getValue().split(",");
            long totalMissing = 0;
            for (String req : required) {
                String reqNorm = normalizeSkill(req.trim());
                long missing = jobApps.stream()
                    .filter(app -> app.getJobSeeker() != null)
                    .filter(app -> !hasSkill(app.getJobSeeker().getSkills(), reqNorm))
                    .count();
                totalMissing += missing;
            }

            if (totalMissing > 0) {
                EmployerInsightDto insight = new EmployerInsightDto();
                insight.setTitle("Skills gap detected");
                String jobTitle = entry.getValue() != null && !entry.getValue().isBlank()
                    ? entry.getValue().split(",")[0].trim()
                    : "Your posting";
                insight.setDescription(String.format(
                    "Your %s posting has %d applicants, but %d are missing required skills.",
                    jobTitle,
                    jobApps.size(), totalMissing));
                insight.setPriority("warning");
                result.add(insight);
            }
        }

        // Insight 3: Average match score
        if (!allApps.isEmpty()) {
            double avgMatch = allApps.stream()
                .mapToDouble(app -> app.getMatchPercentage() != null ? app.getMatchPercentage().doubleValue() : 0.0)
                .average().orElse(0.0);

            if (avgMatch > 0) {
                EmployerInsightDto insight = new EmployerInsightDto();
                insight.setTitle("Average matching score");
                insight.setDescription(String.format(
                    "Your applicants have an average matching score of %.0f%%.",
                    avgMatch));
                insight.setPriority("info");
                result.add(insight);
            }
        }

        // Insight 4: Geographic concentration of applicants
        Map<String, Long> byMuni = allApps.stream()
            .filter(app -> app.getJobSeeker() != null && app.getJobSeeker().getMunicipality() != null)
            .collect(Collectors.groupingBy(
                app -> app.getJobSeeker().getMunicipality().name(),
                Collectors.counting()));

        if (!byMuni.isEmpty()) {
            String topMuni = byMuni.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            if (topMuni != null && byMuni.get(topMuni) > allApps.size() * 0.4) {
                EmployerInsightDto insight = new EmployerInsightDto();
                insight.setTitle("Applicants concentrated in one location");
                insight.setDescription(String.format(
                    "Most applicants (%d) are located in %s.",
                    byMuni.get(topMuni), topMuni));
                insight.setPriority("info");
                result.add(insight);
            }
        }

        return result;
    }

    // ============================================================================
    // Exportable Report
    // ============================================================================

    @Transactional(readOnly = true)
    public EmployerAnalyticsExportDto export(Long employerId, String dateRange) {
        LocalDateTime[] dateBounds = parseDateRange(dateRange);
        LocalDateTime dateFrom = dateBounds[0];
        LocalDateTime dateTo = dateBounds[1];

        EmployerAnalyticsOverviewDto overview = overview(employerId, dateRange);
        EmployerApplicantFunnelDto funnel = funnel(employerId, dateRange);
        EmployerMatchingInsightsDto matching = matchingInsights(employerId, dateRange);
        EmployerGeographicInsightsDto geo = geographicInsights(employerId, dateRange);
        List<EmployerInsightDto> insightList = insights(employerId, dateRange);

        // Get jobs with their performance data
        List<Long> jobIds = jobPostRepository.findByEmployerId(employerId).stream()
            .map(JobPost::getId).collect(Collectors.toList());

        List<JobPost> jobs = jobPostRepository.findAllById(jobIds);
        List<EmployerJobPerformanceDto> jobPerformances = jobs.stream()
            .map(job -> {
                List<JobApplication> apps = applicationRepository.findByJobPostId(job.getId());
                // Filter by date range
                if (dateFrom != null || dateTo != null) {
                    apps = apps.stream()
                        .filter(app -> isWithinDateRange(app.getAppliedAt(), dateFrom, dateTo))
                        .collect(Collectors.toList());
                }
                long total = apps.size();
                EmployerJobPerformanceDto dto = new EmployerJobPerformanceDto();
                dto.setId(job.getId());
                dto.setJobTitle(job.getJobTitle());
                dto.setMunicipality(job.getMunicipality() != null ? job.getMunicipality().name() : null);
                dto.setEmploymentType(job.getEmploymentType() != null ? job.getEmploymentType().name() : null);
                dto.setDatePosted(job.getCreatedAt());
                dto.setStatus(job.isActive() ? "Active" : "Inactive");
                dto.setTotalApplicants(total);
                dto.setNewApplicants(countByStatusInList(apps, ApplicationStatus.SUBMITTED));
                dto.setShortlisted(countByStatusInList(apps, ApplicationStatus.SHORTLISTED));
                dto.setInterviewed(countByStatusInList(apps, ApplicationStatus.INTERVIEW));
                dto.setAccepted(countByStatusInList(apps, ApplicationStatus.ACCEPTED));
                dto.setRejected(countByStatusInList(apps, ApplicationStatus.REJECTED));
                // Average match score
                if (total > 0) {
                    double sum = apps.stream()
                        .mapToDouble(app -> app.getMatchPercentage() != null ? app.getMatchPercentage().doubleValue() : 0.0)
                        .sum();
                    dto.setAverageMatchScore(sum / total);
                }
                dto.setActions("View Job, View Applicants, View Analytics");
                return dto;
            }).collect(Collectors.toList());

        EmployerAnalyticsExportDto dto = new EmployerAnalyticsExportDto();
        dto.setOverview(overview);
        dto.setFunnel(funnel);
        dto.setMatching(matching);
        dto.setGeographic(geo);
        dto.setInsights(insightList);
        dto.setJobPerformances(jobPerformances);
        dto.setDateRange(dateRange);
        dto.setGeneratedAt(LocalDateTime.now());
        return dto;
    }

    private long countByStatusInList(List<JobApplication> apps, ApplicationStatus status) {
        long count = 0;
        for (JobApplication app : apps) {
            if (app.getApplicationStatus() == status) count++;
        }
        return count;
    }

    /* ---------- ownership and helpers -------------------------------------- */

    /** Resolves the owning employer from the job post. Throws if the employer does not own the job. */
    private void requireJobOwner(JobPost job, Long employerId) {
        EmployerProfile employer = job.getEmployer();
        boolean owns = employer != null && employerId != null
            && (employerId.equals(employer.getId())
                || (employer.getUser() != null && employerId.equals(employer.getUser().getId())));
        if (!owns) {
            // A cross-tenant read is an authorization failure (403), not a server
            // fault: the generic 500 handler used to answer this, which hid the
            // real cause from the client and the log.
            throw new org.springframework.security.access.AccessDeniedException(
                "This job belongs to another employer");
        }
    }

    /** Parse skills text into normalized list */
    private List<String> parseSkills(String skillsText) {
        if (skillsText == null || skillsText.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(skillsText.split(","))
            .stream()
            .map(s -> s.trim().toUpperCase())
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private String normalizeSkill(String skill) {
        if (skill == null) return "";
        return skill.trim().toUpperCase();
    }

    private boolean hasSkill(String applicantSkills, String requiredSkill) {
        if (applicantSkills == null) return false;
        String normalized = normalizeSkill(applicantSkills);
        // Check if the required skill is contained in the applicant's skills
        for (String skill : applicantSkills.split(",")) {
            if (normalizeSkill(skill).equals(normalizeSkill(requiredSkill))) {
                return true;
            }
        }
        return false;
    }
}