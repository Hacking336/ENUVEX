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

    /** Applicants of one job. Only the employer that owns the job may read them. */
    public List<ApplicantDto> getJobApplicants(Long jobId, Long employerId, String sortBy) {
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        requireJobOwner(job, employerId);

        return applicationRepository.findByJobPostId(jobId).stream()
            .sorted(comparatorFor(sortBy))
            .map(this::mapToApplicantDto)
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

    private Comparator<JobApplication> comparatorFor(String sortBy) {
        switch (sortBy == null ? "" : sortBy) {
            case "match":
                return Comparator.comparingDouble((JobApplication a) ->
                    a.getMatchPercentage() != null ? a.getMatchPercentage().doubleValue() : 0.0).reversed();
            case "skills":
                return Comparator.comparing((JobApplication a) -> a.getJobSeeker().getSkills(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "experience":
                return Comparator.comparing((JobApplication a) -> a.getJobSeeker().getWorkExperience(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "location":
                return Comparator.comparing((JobApplication a) ->
                        a.getJobSeeker().getMunicipality() == null ? null : a.getJobSeeker().getMunicipality().name(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "availability":
                return Comparator.comparing((JobApplication a) ->
                        a.getJobSeeker().getAvailability() == null ? null : a.getJobSeeker().getAvailability().name(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default:
                return Comparator.comparing(JobApplication::getAppliedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        }
    }

    private ApplicationResponse mapToResponse(JobApplication application) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setJobTitle(application.getJobPost().getJobTitle());
        response.setCompanyName(application.getJobPost().getEmployer().getBusinessName());
        response.setAppliedAt(application.getAppliedAt());
        response.setMatchPercentage(
            application.getMatchPercentage() != null ? application.getMatchPercentage().doubleValue() : null);
        response.setStatus(application.getApplicationStatus());
        return response;
    }

    private ApplicantDto mapToApplicantDto(JobApplication application) {
        JobSeekerProfile seeker = application.getJobSeeker();
        ApplicantDto dto = new ApplicantDto();
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
        dto.setMatchPercentage(
            application.getMatchPercentage() != null ? application.getMatchPercentage().doubleValue() : null);
        dto.setContactNumber(seeker.getContactNumber());
        return dto;
    }
}
