package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.ApplicationStatus;
import com.jobskills.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public JobApplication updateApplicationStatus(Long applicationId, ApplicationStatus status) {
        JobApplication application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        application.setApplicationStatus(status);
        return applicationRepository.save(application);
    }

    public List<JobApplication> getApplicationsBySeeker(Long seekerId) {
        return applicationRepository.findByJobSeekerId(seekerId);
    }

    public List<JobApplication> getApplicationsByJob(Long jobId) {
        return applicationRepository.findByJobPostId(jobId);
    }

    public Optional<JobApplication> getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId);
    }

    public List<ApplicationResponse> getSeekerApplications(Long seekerId) {
        List<JobApplication> applications = applicationRepository.findByJobSeekerId(seekerId);
        return applications.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<ApplicantDto> getJobApplicants(Long jobId, String sortBy) {
        List<JobApplication> applications = applicationRepository.findByJobPostId(jobId);

        // Sort based on parameter
        Comparator<JobApplication> comparator;
        switch (sortBy) {
            case "match":
                comparator = Comparator.comparingDouble((JobApplication a) -> a.getMatchPercentage() != null ? a.getMatchPercentage().doubleValue() : 0.0).reversed();
                break;
            case "skills":
                comparator = Comparator.comparing(a -> a.getJobSeeker().getSkills());
                break;
            case "experience":
                comparator = Comparator.comparing(a -> a.getJobSeeker().getWorkExperience());
                break;
            case "location":
                comparator = Comparator.comparing(a -> a.getJobSeeker().getMunicipality().name());
                break;
            case "availability":
                comparator = Comparator.comparing(a -> a.getJobSeeker().getAvailability());
                break;
            default:
                comparator = Comparator.comparing(JobApplication::getAppliedAt).reversed();
        }

        return applications.stream()
            .sorted(comparator)
            .map(this::mapToApplicantDto)
            .collect(Collectors.toList());
    }

    private ApplicationResponse mapToResponse(JobApplication application) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setJobTitle(application.getJobPost().getJobTitle());
        response.setCompanyName(application.getJobPost().getEmployer().getBusinessName());
        response.setAppliedAt(application.getAppliedAt());
        response.setMatchPercentage(application.getMatchPercentage().doubleValue());
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
        dto.setMatchPercentage(application.getMatchPercentage().doubleValue());
        dto.setContactNumber(seeker.getContactNumber());
        return dto;
    }
}