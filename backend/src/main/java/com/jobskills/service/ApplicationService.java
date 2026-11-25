package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final EmployerProfileRepository employerRepository;
    private final JobPostRepository jobPostRepository;
    private final MatchingEngine matchingEngine;

    /** Status changes are only allowed for the employer that owns the job. */
    @Transactional
    public JobApplication updateApplicationStatus(Long applicationId, Long employerId, ApplicationStatus status) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        requireJobOwner(application.getJobPost(), employerId);
        application.setApplicationStatus(status);
        return applicationRepository.save(application);
    }

    /** Applications of one seeker, addressed by the logged-in user id. */
    public List<ApplicationResponse> getSeekerApplications(Long seekerUserId) {
        JobSeekerProfile seeker = seekerRepository.findByUserId(seekerUserId)
            .orElseThrow(() -> new IllegalArgumentException("No job seeker profile for the current account"));
        return applicationRepository.findByJobSeekerId(seeker.getId()).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Applicants of one job. Only the employer that owns the job may read them.
     *
     * The match is recalculated here against the current job requirements and the
     * applicant's current profile, so an edited job or profile never leaves a
     * stale score on screen. The stored application score is kept untouched.
     */
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

    private void requireJobOwner(JobPost job, Long employerId) {
        if (employerId == null || !job.getEmployer().getId().equals(employerId)) {
            throw new AccessDeniedException("This job belongs to another employer");
        }
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

    private ApplicationResponse mapToResponse(JobApplication application) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setJobPostId(application.getJobPost().getId());
        response.setJobTitle(application.getJobPost().getJobTitle());
        response.setCompanyName(application.getJobPost().getEmployer().getBusinessName());
        response.setAppliedAt(application.getAppliedAt());
        response.setMatchPercentage(
            application.getMatchPercentage() != null ? application.getMatchPercentage().doubleValue() : null);
        response.setStatus(application.getApplicationStatus());
        return response;
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
        return dto;
    }
}
