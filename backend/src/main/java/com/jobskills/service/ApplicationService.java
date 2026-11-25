package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.model.enums.UserType;
import com.jobskills.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Application lifecycle, history and interviews.
 *
 * Every status change goes through one validated transition table, is written to
 * the immutable history with its actor, and produces exactly one notification per
 * event (deduplicated in the database). The same service serves the seeker's
 * tracker, the employer's applicant screens and the dashboards, so all three see
 * the same status and the same timeline.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    /** What an employer may do from each state. Returning to an earlier stage is allowed. */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = new EnumMap<>(ApplicationStatus.class);

    static {
        TRANSITIONS.put(ApplicationStatus.SUBMITTED,
            EnumSet.of(ApplicationStatus.REVIEWING, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.REVIEWING,
            EnumSet.of(ApplicationStatus.SUBMITTED, ApplicationStatus.SHORTLISTED,
                ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.SHORTLISTED,
            EnumSet.of(ApplicationStatus.REVIEWING, ApplicationStatus.INTERVIEW,
                ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.INTERVIEW,
            EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.ACCEPTED,
            EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.REJECTED,
            EnumSet.of(ApplicationStatus.REVIEWING, ApplicationStatus.SHORTLISTED));
        TRANSITIONS.put(ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
    }

    private final JobApplicationRepository applicationRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final EmployerProfileRepository employerRepository;
    private final JobPostRepository jobPostRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final ApplicationInterviewRepository interviewRepository;
    private final MatchingEngine matchingEngine;
    private final NotificationService notificationService;

    /* ---------- reads ------------------------------------------------------ */

    /** Applications of one seeker, enriched with the job, latest event and interview. */
    @Transactional
    public List<ApplicationResponse> getSeekerApplications(Long seekerUserId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        Map<Long, List<ApplicationStatusHistory>> historyByApplication = new HashMap<>();
        List<JobApplication> applications = applicationRepository.findByJobSeekerId(seeker.getId());

        for (JobApplication application : applications) {
            historyByApplication.put(application.getId(),
                historyRepository.findByApplicationIdOrderByCreatedAtAscIdAsc(application.getId()));
        }

        return applications.stream()
            .map(application -> toResponse(application, historyByApplication.getOrDefault(application.getId(), List.of())))
            .sorted(Comparator.comparing(ApplicationResponse::getAppliedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    /**
     * Full application record. A seeker may read only their own application, an
     * employer only applications to jobs they own, and an admin may read any.
     */
    @Transactional
    public ApplicationDetailDto getApplication(Long applicationId, Long userId, UserType userType) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        boolean isSeeker = userType == UserType.JOB_SEEKER && ownsAsSeeker(application, userId);
        boolean isEmployer = userType == UserType.EMPLOYER && ownsAsEmployer(application, userId);
        boolean isAdmin = userType == UserType.ADMIN;

        if (!isSeeker && !isEmployer && !isAdmin) {
            throw new AccessDeniedException("This application belongs to another account");
        }

        ApplicationDetailDto dto = toDetail(application, isSeeker, isEmployer);
        dto.setTimeline(buildTimeline(application));
        interviewRepository.findByApplicationId(applicationId).ifPresent(interview ->
            dto.setInterview(toInterviewDto(interview)));
        return dto;
    }

    public List<ApplicationTimelineDto> getTimeline(Long applicationId, Long userId, UserType userType) {
        return getApplication(applicationId, userId, userType).getTimeline();
    }

    public InterviewDto getInterview(Long applicationId, Long userId, UserType userType) {
        return getApplication(applicationId, userId, userType).getInterview();
    }

    /** Applicants of one job. Only the employer that owns the job may read them. */
    @Transactional
    public List<ApplicantDto> getJobApplicants(Long jobId, Long employerId, String sortBy) {
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        requireJobOwner(job, employerId);

        return applicationRepository.findByJobPostId(jobId).stream()
            .map(application -> mapToApplicantDto(application, job))
            .sorted(comparatorFor(sortBy))
            .collect(Collectors.toList());
    }

    public long countApplicationsForEmployer(Long employerId) {
        List<Long> jobIds = jobPostRepository.findByEmployerId(employerId).stream()
            .map(JobPost::getId)
            .collect(Collectors.toList());
        if (jobIds.isEmpty()) {
            return 0L;
        }
        return applicationRepository.countByJobPostIdIn(jobIds);
    }

    /* ---------- employer actions ------------------------------------------- */

    @Transactional
    public ApplicationDetailDto updateStatus(Long applicationId, Long employerId,
                                             ApplicationStatus target, String message) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        EmployerProfile employer = requireJobOwner(application.getJobPost(), employerId);

        ApplicationStatus current = application.getApplicationStatus();
        if (current == target) {
            throw new IllegalArgumentException("This application is already " + target.label().toLowerCase(Locale.ROOT) + ".");
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalArgumentException("You cannot move an application from "
                + current.label() + " to " + target.label() + ".");
        }
        if (target == ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Only the job seeker can withdraw an application.");
        }

        String note = blankToNull(message);
        application.setApplicationStatus(target);
        applicationRepository.save(application);
        ApplicationStatusHistory history = record(application, current, target,
            "STATUS_CHANGED", employer.getUser().getId(), "EMPLOYER", employer.getBusinessName(), note);

        JobSeekerProfile seeker = application.getJobSeeker();
        notificationService.notify(seeker.getUser(), NotificationService.APPLICATION_STATUS,
            "Application " + target.label().toLowerCase(Locale.ROOT),
            statusMessage(application, target),
            "/job-seeker/application.html?id=" + application.getId(),
            "APPLICATION", application.getId(), "app-" + application.getId() + "-h" + history.getId());

        return getApplication(applicationId, employerId, UserType.EMPLOYER);
    }

    /* ---------- job seeker actions ----------------------------------------- */

    @Transactional
    public ApplicationDetailDto withdraw(Long applicationId, Long seekerUserId) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        if (!application.getJobSeeker().getId().equals(seeker.getId())) {
            throw new AccessDeniedException("This application belongs to another account");
        }

        ApplicationStatus current = application.getApplicationStatus();
        if (current == ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("This application is already withdrawn.");
        }
        if (!current.isOpen()) {
            throw new IllegalArgumentException("An application that is " + current.label().toLowerCase(Locale.ROOT)
                + " can no longer be withdrawn.");
        }

        application.setApplicationStatus(ApplicationStatus.WITHDRAWN);
        applicationRepository.save(application);
        ApplicationStatusHistory history = record(application, current, ApplicationStatus.WITHDRAWN,
            "WITHDRAWN", seeker.getUser().getId(), "JOB_SEEKER", seeker.getFullName(), null);

        notificationService.notify(seeker.getUser(), NotificationService.APPLICATION_WITHDRAWN,
            "Application withdrawn",
            "You withdrew your application for " + application.getJobPost().getJobTitle() + ".",
            "/job-seeker/application.html?id=" + application.getId(),
            "APPLICATION", application.getId(), "app-" + application.getId() + "-h" + history.getId());

        EmployerProfile employer = application.getJobPost().getEmployer();
        notificationService.notify(employer.getUser(), NotificationService.APPLICANT_WITHDRAWN,
            "Applicant withdrew",
            seeker.getFullName() + " withdrew their application for "
                + application.getJobPost().getJobTitle() + ".",
            "/employer/applicants.html?applicationId=" + application.getId(),
            "APPLICATION", application.getId(), "app-" + application.getId() + "-withdrawn-employer");

        return getApplication(applicationId, seekerUserId, UserType.JOB_SEEKER);
    }

    /* ---------- interviews -------------------------------------------------- */

    @Transactional
    public ApplicationDetailDto scheduleInterview(Long applicationId, Long employerId, InterviewRequest request) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        EmployerProfile employer = requireJobOwner(application.getJobPost(), employerId);

        if (interviewRepository.findByApplicationId(applicationId).isPresent()) {
            throw new IllegalArgumentException("This applicant already has an interview. Reschedule it instead.");
        }
        validateInterview(request);

        ApplicationStatus current = application.getApplicationStatus();
        if (!current.isOpen()) {
            throw new IllegalArgumentException("An application that is " + current.label().toLowerCase(Locale.ROOT)
                + " cannot be invited to an interview.");
        }

        ApplicationInterview interview = saveInterview(applicationId, request, employer, "SCHEDULED");
        ApplicationStatus newStatus = ApplicationStatus.INTERVIEW;
        if (current != newStatus) {
            application.setApplicationStatus(newStatus);
            applicationRepository.save(application);
        }

        ApplicationStatusHistory history = record(application, current, newStatus,
            "INTERVIEW_SCHEDULED", employer.getUser().getId(), "EMPLOYER", employer.getBusinessName(),
            interviewSummary(interview, request.getEmployerMessage()));

        notificationService.notify(application.getJobSeeker().getUser(), NotificationService.INTERVIEW_INVITE,
            "Interview invitation",
            "You have been invited for an interview for " + application.getJobPost().getJobTitle()
                + " on " + WHEN.format(interview.getInterviewAt()) + ".",
            "/job-seeker/application.html?id=" + application.getId(),
            "APPLICATION", application.getId(), "app-" + application.getId() + "-h" + history.getId());

        return getApplication(applicationId, employerId, UserType.EMPLOYER);
    }

    @Transactional
    public ApplicationDetailDto rescheduleInterview(Long applicationId, Long employerId, InterviewRequest request) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        EmployerProfile employer = requireJobOwner(application.getJobPost(), employerId);

        ApplicationInterview existing = interviewRepository.findByApplicationId(applicationId)
            .filter(interview -> !"CANCELLED".equalsIgnoreCase(interview.getStatus()))
            .orElseThrow(() -> new IllegalArgumentException("There is no active interview to reschedule."));
        validateInterview(request);

        String previousSchedule = "Previously " + WHEN.format(existing.getInterviewAt())
            + " (" + humanType(existing.getInterviewType()) + ").";
        LocalDateTime previousAt = existing.getInterviewAt();

        ApplicationInterview interview = saveInterview(applicationId, request, employer, "RESCHEDULED");
        if (application.getApplicationStatus() != ApplicationStatus.INTERVIEW) {
            application.setApplicationStatus(ApplicationStatus.INTERVIEW);
            applicationRepository.save(application);
        }

        String message = previousSchedule
            + (previousAt != null ? " New schedule: " + WHEN.format(request.getInterviewAt()) + "." : "");
        if (blankToNull(request.getEmployerMessage()) != null) {
            message += " " + request.getEmployerMessage().trim();
        }
        ApplicationStatusHistory history = record(application, ApplicationStatus.INTERVIEW,
            ApplicationStatus.INTERVIEW, "INTERVIEW_RESCHEDULED", employer.getUser().getId(),
            "EMPLOYER", employer.getBusinessName(), message);

        notificationService.notify(application.getJobSeeker().getUser(), NotificationService.INTERVIEW_RESCHEDULED,
            "Interview rescheduled",
            "Your interview for " + application.getJobPost().getJobTitle() + " moved to "
                + WHEN.format(interview.getInterviewAt()) + ".",
            "/job-seeker/application.html?id=" + application.getId(),
            "APPLICATION", application.getId(), "app-" + application.getId() + "-h" + history.getId());

        return getApplication(applicationId, employerId, UserType.EMPLOYER);
    }

    @Transactional
    public ApplicationDetailDto cancelInterview(Long applicationId, Long employerId, String reason) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        EmployerProfile employer = requireJobOwner(application.getJobPost(), employerId);

        ApplicationInterview interview = interviewRepository.findByApplicationId(applicationId)
            .filter(item -> !"CANCELLED".equalsIgnoreCase(item.getStatus()))
            .orElseThrow(() -> new IllegalArgumentException("There is no active interview to cancel."));

        interview.setStatus("CANCELLED");
        interviewRepository.save(interview);

        ApplicationStatus current = application.getApplicationStatus();
        ApplicationStatus newStatus = current == ApplicationStatus.INTERVIEW
            ? ApplicationStatus.SHORTLISTED : current;
        if (current != newStatus) {
            application.setApplicationStatus(newStatus);
            applicationRepository.save(application);
        }

        String message = "The interview scheduled for " + WHEN.format(interview.getInterviewAt())
            + " was cancelled.";
        if (blankToNull(reason) != null) {
            message += " " + reason.trim();
        }
        ApplicationStatusHistory history = record(application, current, newStatus,
            "INTERVIEW_CANCELLED", employer.getUser().getId(), "EMPLOYER", employer.getBusinessName(), message);

        notificationService.notify(application.getJobSeeker().getUser(), NotificationService.INTERVIEW_CANCELLED,
            "Interview cancelled",
            "Your interview for " + application.getJobPost().getJobTitle() + " was cancelled.",
            "/job-seeker/application.html?id=" + application.getId(),
            "APPLICATION", application.getId(), "app-" + application.getId() + "-h" + history.getId());

        return getApplication(applicationId, employerId, UserType.EMPLOYER);
    }

    /* ---------- mapping ----------------------------------------------------- */

    private ApplicationResponse toResponse(JobApplication application, List<ApplicationStatusHistory> history) {
        JobPost job = application.getJobPost();
        JobSeekerProfile seeker = application.getJobSeeker();
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setJobPostId(job.getId());
        response.setJobTitle(job.getJobTitle());
        response.setCompanyName(job.getEmployer() != null ? job.getEmployer().getBusinessName() : null);
        response.setAppliedAt(application.getAppliedAt());
        response.setStatus(application.getApplicationStatus());
        response.setStatusLabel(application.getApplicationStatus().label());
        response.setMunicipality(job.getMunicipality());
        response.setBarangay(job.getBarangay());
        response.setEmploymentType(job.getEmploymentType() != null ? job.getEmploymentType().name() : null);
        response.setSalaryMin(job.getSalaryMin());
        response.setSalaryMax(job.getSalaryMax());
        response.setCanWithdraw(application.getApplicationStatus().isOpen());

        MatchResultDto match = matchingEngine.calculateMatch(seeker, job);
        response.setMatchPercentage(match.getOverallMatch());
        response.setMatchClassification(match.getClassification());

        LocalDateTime last = history.isEmpty()
            ? application.getAppliedAt()
            : history.get(history.size() - 1).getCreatedAt();
        response.setLastUpdatedAt(last != null ? last : application.getUpdatedAt());
        if (!history.isEmpty()) {
            ApplicationStatusHistory latest = history.get(history.size() - 1);
            response.setLatestEventTitle(eventTitle(latest));
            response.setLatestEventDescription(eventDescription(latest, application));
        }

        interviewRepository.findByApplicationId(application.getId())
            .filter(interview -> !"CANCELLED".equalsIgnoreCase(interview.getStatus()))
            .ifPresent(interview -> response.setInterview(toInterviewDto(interview)));
        return response;
    }

    private ApplicationDetailDto toDetail(JobApplication application, boolean seeker, boolean employer) {
        JobPost job = application.getJobPost();
        JobSeekerProfile profile = application.getJobSeeker();
        ApplicationDetailDto dto = new ApplicationDetailDto();
        dto.setId(application.getId());
        dto.setJobPostId(job.getId());
        dto.setJobTitle(job.getJobTitle());
        dto.setJobDescription(job.getJobDescription());
        dto.setCompanyName(job.getEmployer() != null ? job.getEmployer().getBusinessName() : null);
        dto.setMunicipality(job.getMunicipality());
        dto.setBarangay(job.getBarangay());
        dto.setEmploymentType(job.getEmploymentType() != null ? job.getEmploymentType().name() : null);
        dto.setSalaryMin(job.getSalaryMin());
        dto.setSalaryMax(job.getSalaryMax());
        dto.setAppliedAt(application.getAppliedAt());
        dto.setLastUpdatedAt(application.getUpdatedAt());
        dto.setStatus(application.getApplicationStatus());
        dto.setStatusLabel(application.getApplicationStatus().label());
        dto.setJobSeekerId(profile.getId());
        dto.setSeekerName(profile.getFullName());
        dto.setViewerIsSeeker(seeker);
        dto.setViewerIsEmployer(employer);
        dto.setCanWithdraw(seeker && application.getApplicationStatus().isOpen());
        dto.setCanManage(employer && application.getApplicationStatus() != ApplicationStatus.WITHDRAWN);

        if (seeker) {
            MatchResultDto match = matchingEngine.calculateMatch(profile, job);
            dto.setMatchPercentage(match.getOverallMatch());
            dto.setMatchClassification(match.getClassification());
            dto.setMatchedSkills(match.getMatchedSkills());
            dto.setMissingSkills(match.getMissingSkills());
        } else if (employer) {
            MatchResultDto match = matchingEngine.calculateMatch(profile, job);
            dto.setMatchPercentage(match.getOverallMatch());
            dto.setMatchClassification(match.getClassification());
            dto.setMatchedSkills(match.getMatchedSkills());
            dto.setMissingSkills(match.getMissingSkills());
            dto.setSeekerEmail(profile.getUser() != null ? profile.getUser().getEmail() : null);
        }

        if (employer) {
            dto.setAllowedNextStatuses(new ArrayList<>(
                TRANSITIONS.getOrDefault(application.getApplicationStatus(), Set.of())));
        }
        return dto;
    }

    private List<ApplicationTimelineDto> buildTimeline(JobApplication application) {
        List<ApplicationStatusHistory> history =
            historyRepository.findByApplicationIdOrderByCreatedAtAscIdAsc(application.getId());

        List<ApplicationTimelineDto> timeline = new ArrayList<>();
        for (ApplicationStatusHistory event : history) {
            ApplicationTimelineDto dto = new ApplicationTimelineDto();
            dto.setId(event.getId());
            dto.setStatus(event.getNewStatus());
            dto.setEventType(event.getEventType());
            dto.setTitle(eventTitle(event));
            dto.setDescription(eventDescription(event, application));
            dto.setMessage(event.getMessage());
            dto.setActorRole(event.getActorRole());
            dto.setActorName(event.getActorName());
            dto.setCreatedAt(event.getCreatedAt());
            dto.setRecorded(true);
            timeline.add(dto);
        }

        ApplicationStatus status = application.getApplicationStatus();
        boolean lastMatches = !history.isEmpty()
            && history.get(history.size() - 1).getNewStatus() == status;
        if (!lastMatches) {
            // No stored transition for the current state: report it without a date
            // rather than inventing one.
            ApplicationTimelineDto current = new ApplicationTimelineDto();
            current.setStatus(status);
            current.setEventType("CURRENT_STATUS");
            current.setTitle("Current status: " + status.label());
            current.setDescription("This status was applied before status history was recorded, "
                + "so its exact time is not available.");
            current.setCreatedAt(null);
            current.setRecorded(false);
            timeline.add(current);
        }
        return timeline;
    }

    private String eventTitle(ApplicationStatusHistory event) {
        String type = event.getEventType() == null ? "" : event.getEventType();
        switch (type) {
            case "SUBMITTED": return "Application submitted";
            case "WITHDRAWN": return "Application withdrawn";
            case "INTERVIEW_SCHEDULED": return "Interview scheduled";
            case "INTERVIEW_RESCHEDULED": return "Interview rescheduled";
            case "INTERVIEW_CANCELLED": return "Interview cancelled";
            case "CURRENT_STATUS": return "Current status: " + event.getNewStatus().label();
            default: return "Moved to " + event.getNewStatus().label();
        }
    }

    private String eventDescription(ApplicationStatusHistory event, JobApplication application) {
        String type = event.getEventType() == null ? "" : event.getEventType();
        switch (type) {
            case "SUBMITTED":
                return "The application was submitted to "
                    + (application.getJobPost().getEmployer() != null
                        ? application.getJobPost().getEmployer().getBusinessName() : "the employer") + ".";
            case "WITHDRAWN":
                return "The application was withdrawn by the job seeker.";
            case "INTERVIEW_SCHEDULED":
                return "An interview invitation was sent to the applicant.";
            case "INTERVIEW_RESCHEDULED":
                return "The interview schedule was changed.";
            case "INTERVIEW_CANCELLED":
                return "The interview was cancelled.";
            default:
                return event.getPreviousStatus() == null ? "Status updated."
                    : "Status changed from " + event.getPreviousStatus().label()
                        + " to " + event.getNewStatus().label() + ".";
        }
    }

    private String statusMessage(JobApplication application, ApplicationStatus status) {
        String job = application.getJobPost().getJobTitle();
        switch (status) {
            case REVIEWING: return "Your application for " + job + " is now under review.";
            case SHORTLISTED: return "You have been shortlisted for " + job + ".";
            case INTERVIEW: return "You have been invited for an interview for " + job + ".";
            case ACCEPTED: return "Your application for " + job + " was accepted. The employer will contact you.";
            case REJECTED: return "Your application for " + job + " was not successful this time.";
            case SUBMITTED: return "Your application for " + job + " is back in the submitted stage.";
            default: return "Your application for " + job + " is now " + status.label().toLowerCase(Locale.ROOT) + ".";
        }
    }

    private ApplicationStatusHistory record(JobApplication application, ApplicationStatus previous,
                                            ApplicationStatus next, String eventType,
                                            Long actorUserId, String actorRole, String actorName, String message) {
        return historyRepository.save(ApplicationStatusHistory.builder()
            .applicationId(application.getId())
            .previousStatus(previous)
            .newStatus(next)
            .eventType(eventType)
            .actorUserId(actorUserId)
            .actorRole(actorRole)
            .actorName(actorName)
            .message(blankToNull(message))
            .build());
    }

    private ApplicationInterview saveInterview(Long applicationId, InterviewRequest request,
                                               EmployerProfile employer, String status) {
        ApplicationInterview interview = interviewRepository.findByApplicationId(applicationId)
            .orElseGet(ApplicationInterview::new);
        interview.setApplicationId(applicationId);
        interview.setInterviewAt(request.getInterviewAt());
        interview.setInterviewType(normalizeType(request.getInterviewType()));
        interview.setLocation(blankToNull(request.getLocation()));
        interview.setMeetingLink(blankToNull(request.getMeetingLink()));
        interview.setInstructions(blankToNull(request.getInstructions()));
        interview.setEmployerMessage(blankToNull(request.getEmployerMessage()));
        interview.setStatus(status);
        interview.setCreatedBy(employer.getUser().getId());
        return interviewRepository.save(interview);
    }

    private void validateInterview(InterviewRequest request) {
        if (request == null || request.getInterviewAt() == null) {
            throw new IllegalArgumentException("Interview date and time are required.");
        }
        if (request.getInterviewAt().isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("Interview date and time must be in the future.");
        }
        String type = normalizeType(request.getInterviewType());
        if ("ONLINE".equals(type)) {
            String link = blankToNull(request.getMeetingLink());
            if (link == null || !(link.startsWith("https://") || link.startsWith("http://"))) {
                throw new IllegalArgumentException("An online interview needs a meeting link starting with http:// or https://.");
            }
        }
        if ("IN_PERSON".equals(type) && blankToNull(request.getLocation()) == null) {
            throw new IllegalArgumentException("An in-person interview needs a location or address.");
        }
    }

    private String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Interview type is required: IN_PERSON, ONLINE or PHONE.");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (value.equals("INPERSON") || value.equals("FACE_TO_FACE")) value = "IN_PERSON";
        if (!value.equals("IN_PERSON") && !value.equals("ONLINE") && !value.equals("PHONE")) {
            throw new IllegalArgumentException("Unknown interview type: " + raw
                + ". Use IN_PERSON, ONLINE or PHONE.");
        }
        return value;
    }

    private String humanType(String raw) {
        if (raw == null) return "interview";
        switch (raw.toUpperCase(Locale.ROOT)) {
            case "ONLINE": return "online";
            case "PHONE": return "phone";
            default: return "in person";
        }
    }

    private String interviewSummary(ApplicationInterview interview, String extra) {
        StringBuilder text = new StringBuilder("Interview on ")
            .append(WHEN.format(interview.getInterviewAt()))
            .append(" (").append(humanType(interview.getInterviewType())).append(").");
        if (interview.getLocation() != null) text.append(" Location: ").append(interview.getLocation()).append('.');
        if (interview.getMeetingLink() != null) text.append(" Meeting link: ").append(interview.getMeetingLink());
        if (blankToNull(extra) != null) text.append(' ').append(extra.trim());
        return text.toString();
    }

    private InterviewDto toInterviewDto(ApplicationInterview interview) {
        InterviewDto dto = new InterviewDto();
        dto.setId(interview.getId());
        dto.setInterviewAt(interview.getInterviewAt());
        dto.setInterviewType(interview.getInterviewType());
        dto.setLocation(interview.getLocation());
        dto.setMeetingLink(interview.getMeetingLink());
        dto.setInstructions(interview.getInstructions());
        dto.setEmployerMessage(interview.getEmployerMessage());
        dto.setStatus(interview.getStatus());
        dto.setUpdatedAt(interview.getUpdatedAt());
        return dto;
    }

    /* ---------- ownership and helpers -------------------------------------- */

    /**
     * Resolves the owning employer from either the employer profile id or the
     * account id, both of which come from the authenticated token. A caller can
     * only ever present their own id, so accepting both forms cannot grant
     * access to another employer's job.
     */
    private EmployerProfile requireJobOwner(JobPost job, Long employerId) {
        EmployerProfile employer = job.getEmployer();
        boolean owns = employer != null && employerId != null
            && (employerId.equals(employer.getId())
                || (employer.getUser() != null && employerId.equals(employer.getUser().getId())));
        if (!owns) {
            throw new AccessDeniedException("This job belongs to another employer");
        }
        return employer;
    }

    private boolean ownsAsSeeker(JobApplication application, Long userId) {
        return application.getJobSeeker().getUser() != null
            && application.getJobSeeker().getUser().getId().equals(userId);
    }

    private boolean ownsAsEmployer(JobApplication application, Long userId) {
        return application.getJobPost().getEmployer() != null
            && application.getJobPost().getEmployer().getUser() != null
            && application.getJobPost().getEmployer().getUser().getId().equals(userId);
    }

    private JobSeekerProfile requireSeeker(Long userId) {
        return seekerRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("No job seeker profile for the current account"));
    }

    private Comparator<ApplicantDto> comparatorFor(String sortBy) {
        switch (sortBy == null ? "" : sortBy) {
            case "match":
                return Comparator.comparingDouble((ApplicantDto a) ->
                    a.getMatchPercentage() != null ? a.getMatchPercentage() : 0.0).reversed();
            case "skills":
                return Comparator.comparing(ApplicantDto::getSkills,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "experience":
                return Comparator.comparing(ApplicantDto::getWorkExperience,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "location":
                return Comparator.comparing((ApplicantDto a) ->
                        a.getLocation() == null ? null : a.getLocation().name(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "availability":
                return Comparator.comparing(ApplicantDto::getAvailability,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "name":
                return Comparator.comparing(ApplicantDto::getFullName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default:
                return Comparator.comparing(ApplicantDto::getAppliedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        }
    }

    private ApplicantDto mapToApplicantDto(JobApplication application, JobPost job) {
        JobSeekerProfile seeker = application.getJobSeeker();
        ApplicantDto dto = new ApplicantDto();
        dto.setApplicationId(application.getId());
        dto.setApplicationStatus(application.getApplicationStatus());
        dto.setAppliedAt(application.getAppliedAt());
        dto.setJobPostId(job.getId());
        dto.setJobTitle(job.getJobTitle());
        dto.setId(seeker.getId());
        dto.setFullName(seeker.getFullName());
        dto.setProfilePhoto(seeker.getProfilePhoto());
        dto.setSkills(seeker.getSkills());
        dto.setEducation(seeker.getEducationLevel() != null ? seeker.getEducationLevel().name().replace("_", " ") : "");
        dto.setWorkExperience(seeker.getWorkExperience());
        dto.setCertifications(seeker.getCertifications());
        dto.setLocation(seeker.getMunicipality());
        dto.setBarangay(seeker.getBarangay());
        dto.setAvailability(seeker.getAvailability() != null ? seeker.getAvailability().name() : null);
        dto.setEmploymentPreference(seeker.getEmploymentTypePreference());
        dto.setExpectedSalary(seeker.getExpectedSalaryMin());
        dto.setContactNumber(seeker.getContactNumber());

        MatchResultDto match = matchingEngine.calculateMatch(seeker, job);
        dto.setMatchPercentage(match.getOverallMatch());
        dto.setMatchClassification(match.getClassification());
        dto.setMatchedSkills(match.getMatchedSkills());
        dto.setMissingSkills(match.getMissingSkills());
        dto.setMatchCriteria(match.getCriteria());

        interviewRepository.findByApplicationId(application.getId())
            .filter(interview -> !"CANCELLED".equalsIgnoreCase(interview.getStatus()))
            .ifPresent(interview -> dto.setInterview(toInterviewDto(interview)));
        return dto;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
