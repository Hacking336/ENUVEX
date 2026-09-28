package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.*;
import com.jobskills.model.enums.*;
import com.jobskills.repository.*;
import com.jobskills.service.MatchingEngine;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobPostRepository jobPostRepository;
    private final EmployerProfileRepository employerRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final JobApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final MatchingEngine matchingEngine;

    @Transactional
    public JobPost createJob(Long employerId, JobPost job) {
        EmployerProfile employer = employerRepository.findById(employerId)
            .orElseThrow(() -> new IllegalArgumentException("Employer not found"));
        job.setEmployer(employer);
        return jobPostRepository.save(job);
    }

    public JobPost getJobById(Long id) {
        return jobPostRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    @Transactional
    public JobPost updateJob(Long id, JobPost jobDetails) {
        JobPost job = getJobById(id);
        if (!job.getEmployer().getId().equals(jobDetails.getEmployer().getId())) {
            throw new IllegalArgumentException("Unauthorized");
        }

        job.setJobTitle(jobDetails.getJobTitle());
        job.setJobCategory(jobDetails.getJobCategory());
        job.setJobDescription(jobDetails.getJobDescription());
        job.setResponsibilities(jobDetails.getResponsibilities());
        job.setRequiredSkills(jobDetails.getRequiredSkills());
        job.setEducationRequirement(jobDetails.getEducationRequirement());
        job.setExperienceRequirement(jobDetails.getExperienceRequirement());
        job.setCertifications(jobDetails.getCertifications());
        job.setSalaryMin(jobDetails.getSalaryMin());
        job.setSalaryMax(jobDetails.getSalaryMax());
        job.setEmploymentType(jobDetails.getEmploymentType());
        job.setWorkSchedule(jobDetails.getWorkSchedule());
        job.setAvailablePositions(jobDetails.getAvailablePositions());
        job.setMunicipality(jobDetails.getMunicipality());
        job.setBarangay(jobDetails.getBarangay());
        job.setJobLocation(jobDetails.getJobLocation());
        job.setIsActive(jobDetails.getIsActive());

        return jobPostRepository.save(job);
    }

    @Transactional
    public void deleteJob(Long id, Long employerId) {
        JobPost job = getJobById(id);
        if (!job.getEmployer().getId().equals(employerId)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        jobPostRepository.delete(job);
    }

    public List<JobPost> getJobsByEmployer(Long employerId) {
        return jobPostRepository.findByEmployerId(employerId);
    }

    public Page<JobResponse> searchJobs(JobSearchRequest search, Long seekerId) {
        Specification<JobPost> spec = (root, query, cb) -> {
            List<javax.persistence.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isActive"), true));

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
            if (search.getEmploymentType() != null && !search.getEmploymentType().isEmpty()) {
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

            return cb.and(predicates.toArray(new javax.persistence.Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
            search.getPage(),
            search.getSize(),
            Sort.by(Sort.Direction.fromString(search.getSortDir()), search.getSortBy())
        );

        Page<JobPost> jobs = jobPostRepository.findAll(spec, pageable);

        if (seekerId != null) {
            // Add match percentage for authenticated seeker
            JobSeekerProfile seeker = seekerRepository.findById(seekerId)
                .orElseThrow(() -> new IllegalArgumentException("Seeker not found"));
            return jobs.map(job -> {
                JobResponse response = mapToResponse(job);
                double match = matchingEngine.calculateMatch(seeker, job).getOverallMatch();
                response.setMatchPercentage(match);
                return response;
            });
        } else {
            // No match percentage for anonymous search
            return jobs.map(this::mapToResponse);
        }
    }

    public JobResponse getJobDetails(Long jobId, Long seekerId) {
        JobPost job = getJobById(jobId);
        if (!job.getIsActive()) {
            throw new IllegalArgumentException("Job is not active");
        }
        JobResponse response = mapToResponse(job);
        if (seekerId != null) {
            JobSeekerProfile seeker = seekerRepository.findById(seekerId)
                .orElseThrow(() -> new IllegalArgumentException("Seeker not found"));
            double match = matchingEngine.calculateMatch(seeker, job).getOverallMatch();
            response.setMatchPercentage(match);
        }
        return response;
    }

    @Transactional
    public SavedJob saveJob(Long jobSeekerId, Long jobId) {
        JobSeekerProfile seeker = seekerRepository.findById(jobSeekerId)
            .orElseThrow(() -> new IllegalArgumentException("Seeker not found"));
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        SavedJob saved = savedJobRepository.findByJobSeekerIdAndJobPostId(jobSeekerId, jobId)
            .orElseGet(() -> new SavedJob());

        saved.setJobSeeker(seeker);
        saved.setJobPost(job);
        return savedJobRepository.save(saved);
    }

    @Transactional
    public void unsaveJob(Long jobSeekerId, Long jobId) {
        savedJobRepository.deleteByJobSeekerIdAndJobPostId(jobSeekerId, jobId);
    }

    public List<SavedJob> getSavedJobs(Long jobSeekerId) {
        return savedJobRepository.findByJobSeekerId(jobSeekerId);
    }

    @Transactional
    public JobApplication applyForJob(Long jobSeekerId, Long jobId) {
        JobSeekerProfile seeker = seekerRepository.findById(jobSeekerId)
            .orElseThrow(() -> new IllegalArgumentException("Seeker not found"));
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        if (!job.getIsActive()) {
            throw new IllegalArgumentException("Job is not active");
        }

        // Check if already applied
        if (applicationRepository.existsByJobSeekerIdAndJobPostId(jobSeekerId, jobId)) {
            throw new IllegalArgumentException("Already applied for this job");
        }

        JobApplication application = JobApplication.builder()
            .jobSeeker(seeker)
            .jobPost(job)
            .matchPercentage(BigDecimal.valueOf(
                matchingEngine.calculateMatch(seeker, job).getOverallMatch()
            ))
            .build();

        return applicationRepository.save(application);
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
        response.setIsActive(job.getIsActive());
        response.setCreatedAt(job.getCreatedAt());
        response.setRequiredSkills(job.getRequiredSkills());
        return response;
    }
}