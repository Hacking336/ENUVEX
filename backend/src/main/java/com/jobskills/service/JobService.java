package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.model.enums.ModerationStatus;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.*;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    /** Largest page a client may request; keeps one call from loading a table. */
    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int requested) {
        return Math.min(Math.max(requested, 1), MAX_PAGE_SIZE);
    }

    private static int clampPage(int requested) {
        return Math.max(requested, 0);
    }

    /** Columns a client is allowed to sort by. Anything else falls back to createdAt. */
    private static final Set<String> SORTABLE_COLUMNS =
        Set.of("createdAt", "jobTitle", "salaryMin", "salaryMax", "availablePositions");

    private final JobPostRepository jobPostRepository;
    private final EmployerProfileRepository employerRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final JobApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final MatchingEngine matchingEngine;
    private final GeoService geoService;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final SuspiciousListingService suspiciousListingService;

    /** Most seekers told about one new post; keeps job creation bounded. */
    private static final int MAX_JOB_RECOMMENDATIONS = 50;
    private static final double MIN_RECOMMENDATION_MATCH = 60.0;

    @Transactional
    public JobPost createJob(Long employerId, JobPostRequest request) {
        EmployerProfile employer = employerRepository.findById(employerId)
            .orElseThrow(() -> new IllegalArgumentException("Employer not found"));

        // Built here rather than deserialized straight from the body so the
        // defaults are real values, not the zero values of a fresh entity.
        JobPost job = new JobPost();
        job.setEmployer(employer);
        job.setJobTitle(requireText(request.getJobTitle(), "Position title"));
        job.setJobCategory(requireValue(request.getJobCategory(), "Job category"));
        job.setJobDescription(requireText(request.getJobDescription(), "Job description"));
        job.setResponsibilities(requireText(request.getResponsibilities(), "Key responsibilities"));
        job.setRequiredSkills(blankToNull(request.getRequiredSkills()));
        job.setEducationRequirement(request.getEducationRequirement());
        job.setExperienceRequirement(blankToNull(request.getExperienceRequirement()));
        job.setCertifications(blankToNull(request.getCertifications()));
        job.setEmploymentType(request.getEmploymentType());
        job.setWorkSchedule(request.getWorkSchedule());
        job.setMunicipality(requireValue(request.getMunicipality(), "Municipality"));
        job.setJobLocation(blankToNull(request.getJobLocation()));
        applyWorkplaceLocation(job, request, true);
        applySalary(job, request);
        job.setAvailablePositions(request.getAvailablePositions() == null || request.getAvailablePositions() < 1
            ? 1 : request.getAvailablePositions());
        // A new post is live unless the employer explicitly saves it paused;
        // an inactive post never shows up in a seeker's search. It is not
        // publicly visible until an administrator approves it, so no seeker is
        // notified at creation time.
        job.setActive(request.getActive() == null || request.getActive());
        job.setModerationStatus(ModerationStatus.PENDING_REVIEW);

        JobPost saved = jobPostRepository.save(job);
        // Deterministic checks run on every submission; they only flag the post
        // for review and never reject it.
        suspiciousListingService.evaluate(saved);
        return saved;
    }

    /** Tells matching seekers about a posting an administrator just approved. */
    @Transactional
    public void announceApprovedJob(JobPost job) {
        if (job != null && job.isActive()
            && job.getModerationStatus() == ModerationStatus.APPROVED) {
            notifyMatchingSeekers(job);
        }
    }

    /**
     * One notification per seeker per new post, written when the post is created.
     * The dedupe key makes a retried create idempotent, and the page never
     * generates recommendations just by being opened.
     */
    private void notifyMatchingSeekers(JobPost job) {
        int sent = 0;
        for (JobSeekerProfile seeker : seekerRepository.findAll(PageRequest.of(0, 200)).getContent()) {
            if (sent >= MAX_JOB_RECOMMENDATIONS) break;
            if (seeker.getUser() == null || !seeker.getUser().isActive()) continue;
            if (applicationRepository.existsByJobSeekerIdAndJobPostId(seeker.getId(), job.getId())) continue;

            MatchResultDto match = matchingEngine.calculateMatch(seeker, job);
            if (match.getOverallMatch() < MIN_RECOMMENDATION_MATCH) continue;

            boolean created = notificationService.notify(seeker.getUser(),
                NotificationService.JOB_RECOMMENDATION,
                "New matching job",
                "New job matching your skills: " + job.getJobTitle()
                    + (job.getEmployer() != null ? " at " + job.getEmployer().getBusinessName() : "")
                    + " (" + Math.round(match.getOverallMatch()) + "% match).",
                "/job-seeker/job-details.html?id=" + job.getId(),
                "JOB_POST", job.getId(),
                "job-" + job.getId() + "-match-" + seeker.getUser().getId());
            if (created) sent++;
        }
    }

    public JobPost getJobById(Long id) {
        return jobPostRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    /**
     * Updates a job. Ownership is decided by the logged-in employer, never by
     * anything in the request body.
     */
    @Transactional
    public JobPost updateJob(Long id, Long employerId, JobPostRequest request) {
        JobPost job = getJobById(id);
        if (employerId == null || !job.getEmployer().getId().equals(employerId)) {
            throw new org.springframework.security.access.AccessDeniedException("This job belongs to another employer");
        }

        if (request.getJobTitle() != null) job.setJobTitle(requireText(request.getJobTitle(), "Position title"));
        if (request.getJobCategory() != null) job.setJobCategory(request.getJobCategory());
        if (request.getJobDescription() != null) job.setJobDescription(requireText(request.getJobDescription(), "Job description"));
        if (request.getResponsibilities() != null) job.setResponsibilities(requireText(request.getResponsibilities(), "Key responsibilities"));
        if (request.getRequiredSkills() != null) job.setRequiredSkills(blankToNull(request.getRequiredSkills()));
        if (request.getEducationRequirement() != null) job.setEducationRequirement(request.getEducationRequirement());
        if (request.getExperienceRequirement() != null) job.setExperienceRequirement(blankToNull(request.getExperienceRequirement()));
        if (request.getCertifications() != null) job.setCertifications(blankToNull(request.getCertifications()));
        if (request.getEmploymentType() != null) job.setEmploymentType(request.getEmploymentType());
        if (request.getWorkSchedule() != null) job.setWorkSchedule(request.getWorkSchedule());
        if (request.getJobLocation() != null) job.setJobLocation(blankToNull(request.getJobLocation()));
        if (request.getSalaryMin() != null) job.setSalaryMin(request.getSalaryMin());
        if (request.getSalaryMax() != null) job.setSalaryMax(request.getSalaryMax());
        if (request.getAvailablePositions() != null) {
            if (request.getAvailablePositions() < 1) {
                throw new IllegalArgumentException("Available positions must be at least 1");
            }
            job.setAvailablePositions(request.getAvailablePositions());
        }
        // Only an explicit value changes the published state. This is what makes
        // an edit that does not touch "active" keep a paused post paused and a
        // live post live.
        if (request.getActive() != null) job.setActive(request.getActive());

        applyWorkplaceLocation(job, request, false);

        // An edit to a rejected or changes-requested posting is a resubmission:
        // it goes back into the review queue instead of staying hidden forever.
        // An approved posting keeps its approval, so ordinary edits never make a
        // live opportunity disappear.
        ModerationStatus current = job.getModerationStatus();
        if (current == ModerationStatus.CHANGES_REQUESTED || current == ModerationStatus.REJECTED) {
            job.setModerationStatus(ModerationStatus.PENDING_REVIEW);
        }

        JobPost saved = jobPostRepository.save(job);
        suspiciousListingService.evaluate(saved);
        return saved;
    }

    @Transactional
    public void deleteJob(Long id, Long employerId) {
        JobPost job = getJobById(id);
        if (employerId == null || !job.getEmployer().getId().equals(employerId)) {
            throw new org.springframework.security.access.AccessDeniedException("This job belongs to another employer");
        }
        jobPostRepository.delete(job);
    }

    public long countActiveJobs() {
        return jobPostRepository.countByIsActiveTrue();
    }

    public List<JobPost> getJobsByEmployer(Long employerId) {
        return jobPostRepository.findByEmployerId(employerId);
    }

    /**
     * @param seekerUserId the logged-in user's id (not the profile id), or null for anonymous search.
     */
    public Page<JobResponse> searchJobs(JobSearchRequest search, Long seekerUserId) {
        Specification<JobPost> spec = buildSpecification(search);
        JobSeekerProfile seeker = seekerUserId != null ? requireSeeker(seekerUserId) : null;

        if (search.getNearLatitude() != null || search.getNearLongitude() != null) {
            return searchJobsNear(spec, search, seeker);
        }

        // Ranking and score filtering need the whole filtered set, so the sort is
        // done on the server before the page is cut. The page size the browser
        // asked for still applies, and the cap keeps a huge table from making one
        // request expensive.
        if (seeker != null && (isMatchSort(search) || minimumScore(search) != null)) {
            return rankJobs(spec, search, seeker);
        }

        Pageable pageable = PageRequest.of(
            clampPage(search.getPage()),
            clampSize(search.getSize()),
            resolveSort(search));

        return jobPostRepository.findAll(spec, pageable).map(job -> mapToResponse(job, seeker));
    }

    /**
     * Recommended jobs for one seeker: every active job that passes the normal
     * filters is scored, filtered by the requested match band and sorted from the
     * highest match down, then paginated. Low matches are never hidden unless the
     * seeker asks for a band.
     */
    public Page<JobResponse> recommendJobs(JobSearchRequest search, Long seekerUserId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        // Always scored; the requested sort (best match by default) and any band
        // filter are applied to the whole filtered set before it is paginated.
        return rankJobs(buildSpecification(search), search, seeker);
    }

    /** Score details and the per-criterion explanation for one job. */
    public MatchResultDto getMatchDetails(Long jobId, Long seekerUserId) {
        JobPost job = getJobById(jobId);
        if (!job.isActive()) {
            throw new IllegalArgumentException("Job is not active");
        }
        if (job.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new IllegalArgumentException("This posting is not available.");
        }
        return matchingEngine.calculateMatch(requireSeeker(seekerUserId), job);
    }

    private static final int MAX_RANKED_JOBS = 1000;

    private Page<JobResponse> rankJobs(Specification<JobPost> spec, JobSearchRequest search,
                                       JobSeekerProfile seeker) {
        int size = clampSize(search.getSize());
        int pageIndex = clampPage(search.getPage());
        Integer minScore = minimumScore(search);

        List<JobResponse> ranked = jobPostRepository
            .findAll(spec, PageRequest.of(0, MAX_RANKED_JOBS, Sort.by(Sort.Direction.DESC, "createdAt")))
            .stream()
            .map(job -> mapToResponse(job, seeker))
            .filter(response -> minScore == null
                || (response.getMatchPercentage() != null && response.getMatchPercentage() >= minScore))
            .sorted(rankingComparator(search))
            .collect(Collectors.toList());

        int from = Math.min(pageIndex * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        return new PageImpl<>(new ArrayList<>(ranked.subList(from, to)),
            PageRequest.of(pageIndex, size), ranked.size());
    }

    /** Orders a scored candidate list by the seeker's chosen sort. */
    private Comparator<JobResponse> rankingComparator(JobSearchRequest search) {
        boolean ascending = "ASC".equalsIgnoreCase(search.getSortDir());
        String sortBy = search.getSortBy() == null ? "createdAt" : search.getSortBy();
        Comparator<JobResponse> comparator;

        // Undisclosed values always sort last, whichever direction is chosen.
        Comparator<BigDecimal> amountOrder = ascending
            ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<String> textOrder = ascending
            ? String.CASE_INSENSITIVE_ORDER : String.CASE_INSENSITIVE_ORDER.reversed();
        Comparator<java.time.LocalDateTime> dateOrder = ascending
            ? Comparator.naturalOrder() : Comparator.reverseOrder();

        switch (sortBy) {
            case "salaryMax":
                comparator = Comparator.comparing(JobResponse::getSalaryMax,
                    Comparator.nullsLast(amountOrder));
                break;
            case "salaryMin":
                comparator = Comparator.comparing(JobResponse::getSalaryMin,
                    Comparator.nullsLast(amountOrder));
                break;
            case "jobTitle":
                comparator = Comparator.comparing(JobResponse::getJobTitle,
                    Comparator.nullsLast(textOrder));
                break;
            case "createdAt":
                comparator = Comparator.comparing(JobResponse::getCreatedAt,
                    Comparator.nullsLast(dateOrder));
                break;
            default:
                // Best match: the highest score first, newest first on a tie.
                return Comparator
                    .comparingDouble((JobResponse response) ->
                        response.getMatchPercentage() == null ? -1.0 : response.getMatchPercentage())
                    .reversed()
                    .thenComparing(JobResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
        }

        return comparator
            .thenComparing(JobResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean isMatchSort(JobSearchRequest search) {
        return "matchPercentage".equalsIgnoreCase(search.getSortBy());
    }

    private Integer minimumScore(JobSearchRequest search) {
        if (search.getMinMatchScore() != null) {
            return Math.max(0, Math.min(100, search.getMinMatchScore()));
        }
        return MatchingEngine.minimumScoreForBand(search.getMatchBand());
    }

    /**
     * Map payload for the active filters.
     *
     * Only jobs with a confirmed pin are returned as markers. The number of
     * matching jobs without a pin is reported separately, so a map never shows
     * an invented marker and the page can tell the seeker where the rest are.
     */
    public JobMapDto searchJobsMap(JobSearchRequest search, Long seekerUserId) {
        Specification<JobPost> spec = buildSpecification(search);
        Sort sort = resolveSort(search);
        JobSeekerProfile seeker = seekerUserId != null ? requireSeeker(seekerUserId) : null;

        long total = jobPostRepository.count(spec);
        long withPin = jobPostRepository.count(withCoordinates(spec));

        List<JobResponse> mapped = jobPostRepository
            .findAll(spec, PageRequest.of(0, 2000, sort)).stream()
            .map(job -> mapToResponse(job, seeker))
            .filter(job -> job.getLatitude() != null && job.getLongitude() != null)
            .collect(Collectors.toCollection(ArrayList::new));

        if (search.getNearLatitude() != null || search.getNearLongitude() != null) {
            double lat = requireCoordinates(search.getNearLatitude(), search.getNearLongitude())[0];
            double lng = requireCoordinates(search.getNearLatitude(), search.getNearLongitude())[1];
            double radiusKm = radiusKm(search.getRadiusKm());
            mapped = mapped.stream()
                .peek(job -> job.setDistanceKm(round(GeoService.distanceKm(
                    lat, lng, job.getLatitude(), job.getLongitude()), 2)))
                .filter(job -> job.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(JobResponse::getDistanceKm))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        int limit = 500;
        boolean truncated = mapped.size() > limit;
        List<JobResponse> markers = truncated ? new ArrayList<>(mapped.subList(0, limit)) : mapped;

        return new JobMapDto(markers, total, Math.max(0, total - withPin), truncated);
    }

    /**
     * Near Me search. Only jobs with real coordinates can be ranked by distance;
     * the seeker's position is used for this request and never stored.
     */
    private Page<JobResponse> searchJobsNear(Specification<JobPost> spec, JobSearchRequest search,
                                             JobSeekerProfile seeker) {
        double[] point = requireCoordinates(search.getNearLatitude(), search.getNearLongitude());
        double radiusKm = radiusKm(search.getRadiusKm());

        List<JobResponse> nearby = new ArrayList<>();
        for (JobPost job : jobPostRepository.findAll(spec)) {
            JobResponse response = mapToResponse(job, seeker);
            if (response.getLatitude() == null || response.getLongitude() == null) {
                continue;
            }
            double distance = GeoService.distanceKm(point[0], point[1], response.getLatitude(), response.getLongitude());
            if (distance <= radiusKm) {
                response.setDistanceKm(round(distance, 2));
                nearby.add(response);
            }
        }
        // A match band applies to Near Me results too; distance still decides order.
        Integer minScore = minimumScore(search);
        if (minScore != null) {
            nearby.removeIf(response -> response.getMatchPercentage() == null
                || response.getMatchPercentage() < minScore);
        }
        nearby.sort(Comparator.comparing(JobResponse::getDistanceKm));

        int size = clampSize(search.getSize());
        int page = clampPage(search.getPage());
        int from = Math.min(page * size, nearby.size());
        int to = Math.min(from + size, nearby.size());
        return new PageImpl<>(nearby.subList(from, to), PageRequest.of(page, size), nearby.size());
    }

    private Specification<JobPost> buildSpecification(JobSearchRequest search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isActive"), true));
            // Only an approved posting is a public opportunity. Pending,
            // changes-requested and rejected posts stay out of every seeker
            // search, map and recommendation.
            predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED));

            if (search.getMunicipality() != null) {
                predicates.add(cb.equal(root.get("municipality"), search.getMunicipality()));
            }
            if (search.getBarangay() != null && !search.getBarangay().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("barangay")), "%" + search.getBarangay().toLowerCase() + "%"));
            }
            if (search.getCategory() != null) {
                predicates.add(cb.equal(root.get("jobCategory"), search.getCategory()));
            }
            if (search.getSkills() != null && !search.getSkills().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("requiredSkills")), "%" + search.getSkills().toLowerCase() + "%"));
            }
            if (search.getMinSalary() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("salaryMax"), search.getMinSalary()));
            }
            if (search.getMaxSalary() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("salaryMin"), search.getMaxSalary()));
            }
            if (search.getEmploymentType() != null) {
                predicates.add(cb.equal(root.get("employmentType"), search.getEmploymentType()));
            }
            if (search.getWorkSchedule() != null) {
                predicates.add(cb.equal(root.get("workSchedule"), search.getWorkSchedule()));
            }
            if (search.getExperience() != null && !search.getExperience().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("experienceRequirement")), "%" + search.getExperience().toLowerCase() + "%"));
            }
            if (search.getEducation() != null && !search.getEducation().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("educationRequirement")), "%" + search.getEducation().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<JobPost> withCoordinates(Specification<JobPost> spec) {
        return spec.and((root, query, cb) -> cb.and(
            cb.isNotNull(root.get("latitude")),
            cb.isNotNull(root.get("longitude"))));
    }

    private Sort resolveSort(JobSearchRequest search) {
        String sortColumn = SORTABLE_COLUMNS.contains(search.getSortBy()) ? search.getSortBy() : "createdAt";
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(search.getSortDir());
        } catch (IllegalArgumentException | NullPointerException e) {
            direction = Sort.Direction.DESC;
        }
        return Sort.by(direction, sortColumn);
    }

    private double radiusKm(Double value) {
        double radius = value == null ? 25 : value;
        if (radius <= 0 || radius > 500) {
            throw new IllegalArgumentException("Distance must be between 0 and 500 km");
        }
        return radius;
    }

    private double[] requireCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Near Me needs both the device latitude and longitude");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("The device location is not a valid coordinate");
        }
        return new double[] {latitude, longitude};
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    public JobResponse getJobDetails(Long jobId, Long seekerUserId) {
        JobPost job = getJobById(jobId);
        if (!job.isActive()) {
            throw new IllegalArgumentException("Job is not active");
        }
        if (job.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new IllegalArgumentException("This posting is not available.");
        }
        return mapToResponse(job, seekerUserId != null ? requireSeeker(seekerUserId) : null);
    }

    @Transactional
    public SavedJob saveJob(Long seekerUserId, Long jobId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        SavedJob saved = savedJobRepository.findByJobSeekerIdAndJobPostId(seeker.getId(), jobId)
            .orElseGet(SavedJob::new);

        saved.setJobSeeker(seeker);
        saved.setJobPost(job);
        return savedJobRepository.save(saved);
    }

    @Transactional
    public void unsaveJob(Long seekerUserId, Long jobId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        savedJobRepository.deleteByJobSeekerIdAndJobPostId(seeker.getId(), jobId);
    }

    public List<SavedJob> getSavedJobs(Long seekerUserId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        return savedJobRepository.findByJobSeekerId(seeker.getId());
    }

    /** Saved jobs with their job details, newest bookmark first. */
    public List<SavedJobResponse> getSavedJobsForSeeker(Long seekerUserId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        return savedJobRepository.findByJobSeekerId(seeker.getId()).stream()
            .sorted(Comparator.comparing(SavedJob::getSavedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(saved -> {
                SavedJobResponse response = new SavedJobResponse();
                response.setId(saved.getId());
                response.setSavedAt(saved.getSavedAt());
                JobPost job = saved.getJobPost();
                response.setJobPostId(job.getId());
                response.setJob(mapToResponse(job, seeker));
                return response;
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public JobApplication applyForJob(Long seekerUserId, Long jobId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        if (!job.isActive()) {
            throw new IllegalArgumentException("Job is not active");
        }
        if (job.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new IllegalArgumentException("This posting is not available.");
        }
        if (applicationRepository.existsByJobSeekerIdAndJobPostId(seeker.getId(), jobId)) {
            throw new IllegalArgumentException("Already applied for this job");
        }

        JobApplication application = JobApplication.builder()
            .jobSeeker(seeker)
            .jobPost(job)
            .matchPercentage(BigDecimal.valueOf(
                matchingEngine.calculateMatch(seeker, job).getOverallMatch()
            ))
            .build();

        JobApplication saved = applicationRepository.save(application);

        historyRepository.save(ApplicationStatusHistory.builder()
            .applicationId(saved.getId())
            .newStatus(ApplicationStatus.SUBMITTED)
            .eventType("SUBMITTED")
            .actorUserId(seeker.getUser().getId())
            .actorRole("JOB_SEEKER")
            .actorName(seeker.getFullName())
            .build());

        notificationService.notify(seeker.getUser(), NotificationService.APPLICATION_SUBMITTED,
            "Application submitted",
            "Your application for " + job.getJobTitle() + " was submitted successfully.",
            "/job-seeker/application.html?id=" + saved.getId(),
            "APPLICATION", saved.getId(), "app-" + saved.getId() + "-submitted");

        if (job.getEmployer() != null) {
            notificationService.notify(job.getEmployer().getUser(), NotificationService.APPLICATION_RECEIVED,
                "New application received",
                seeker.getFullName() + " applied for " + job.getJobTitle() + ".",
                "/employer/applicants.html?applicationId=" + saved.getId(),
                "APPLICATION", saved.getId(), "app-" + saved.getId() + "-received");
        }
        return saved;
    }

    private void applySalary(JobPost job, JobPostRequest request) {
        BigDecimal min = request.getSalaryMin();
        BigDecimal max = request.getSalaryMax();
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw new IllegalArgumentException("Maximum salary cannot be lower than minimum salary");
        }
        job.setSalaryMin(min);
        job.setSalaryMax(max);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private <T> T requireValue(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Resolves the seeker profile from the logged-in user id. */
    private JobSeekerProfile requireSeeker(Long userId) {
        return seekerRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("No job seeker profile for the current account"));
    }

    /** Adds the full, explainable match result for a signed-in seeker. */
    private JobResponse mapToResponse(JobPost job, JobSeekerProfile seeker) {
        JobResponse response = mapToResponse(job);
        if (seeker != null) {
            MatchResultDto match = matchingEngine.calculateMatch(seeker, job);
            response.setMatchPercentage(match.getOverallMatch());
            response.setMatchClassification(match.getClassification());
            response.setMatchedSkills(match.getMatchedSkills());
            response.setMissingSkills(match.getMissingSkills());
            response.setMatchCriteria(match.getCriteria());
        }
        return response;
    }

    /**
     * Applies the employer's workplace selection and validates it against the
     * PSGC reference data. A job only gets coordinates when the employer
     * confirmed a pin; text-only posts keep working unchanged.
     */
    private void applyWorkplaceLocation(JobPost job, JobPostRequest request, boolean creating) {
        if (Boolean.TRUE.equals(request.getClearLocation())) {
            job.setLatitude(null);
            job.setLongitude(null);
            job.setMunicipalityCode(null);
            job.setBarangayCode(null);
            return;
        }

        boolean touched = creating
            || request.getMunicipality() != null
            || request.getMunicipalityCode() != null
            || request.getBarangay() != null
            || request.getBarangayCode() != null
            || request.getLatitude() != null
            || request.getLongitude() != null;
        if (!touched) {
            return;
        }

        Municipality municipality = request.getMunicipality() != null
            ? request.getMunicipality() : job.getMunicipality();
        String municipalityCode = request.getMunicipalityCode() != null
            ? request.getMunicipalityCode() : job.getMunicipalityCode();
        String barangayCode = request.getBarangayCode() != null
            ? request.getBarangayCode() : job.getBarangayCode();
        String barangayName = request.getBarangay() != null
            ? request.getBarangay() : job.getBarangay();
        Double latitude = request.getLatitude() != null ? request.getLatitude() : job.getLatitude();
        Double longitude = request.getLongitude() != null ? request.getLongitude() : job.getLongitude();

        GeoService.ResolvedLocation resolved = geoService.validate(
            municipality, municipalityCode, barangayCode, barangayName, latitude, longitude);

        job.setMunicipality(municipality);
        job.setMunicipalityCode(resolved.municipalityCode());
        job.setBarangayCode(resolved.barangayCode());
        job.setBarangay(resolved.barangayName() != null
            ? resolved.barangayName()
            : blankToNull(barangayName));
        job.setLatitude(latitude);
        job.setLongitude(longitude);
    }

    private JobResponse mapToResponse(JobPost job) {
        JobResponse response = new JobResponse();
        response.setId(job.getId());
        response.setJobTitle(job.getJobTitle());
        response.setCompanyName(job.getEmployer().getBusinessName());
        response.setCategory(job.getJobCategory());
        response.setDescription(job.getJobDescription());
        response.setSalaryMin(job.getSalaryMin());
        response.setSalaryMax(job.getSalaryMax());
        response.setEmploymentType(job.getEmploymentType() != null ? job.getEmploymentType().name() : null);
        response.setWorkSchedule(job.getWorkSchedule() != null ? job.getWorkSchedule().name() : null);
        response.setAvailablePositions(job.getAvailablePositions());
        response.setMunicipality(job.getMunicipality());
        response.setBarangay(job.getBarangay());
        response.setJobLocation(job.getJobLocation());
        response.setLatitude(job.getLatitude());
        response.setLongitude(job.getLongitude());
        response.setMunicipalityCode(job.getMunicipalityCode());
        response.setBarangayCode(job.getBarangayCode());
        response.setActive(job.isActive());
        response.setCreatedAt(job.getCreatedAt());
        response.setRequiredSkills(job.getRequiredSkills());
        response.setModerationStatus(job.getModerationStatus());
        return response;
    }
}
