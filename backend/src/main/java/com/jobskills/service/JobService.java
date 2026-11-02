package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.*;
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

    /** Columns a client is allowed to sort by. Anything else falls back to createdAt. */
    private static final Set<String> SORTABLE_COLUMNS =
        Set.of("createdAt", "jobTitle", "salaryMin", "salaryMax", "availablePositions");

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

    /**
     * Updates a job. Ownership is decided by the logged-in employer, never by
     * anything in the request body.
     */
    @Transactional
    public JobPost updateJob(Long id, Long employerId, JobPost jobDetails) {
        JobPost job = getJobById(id);
        if (employerId == null || !job.getEmployer().getId().equals(employerId)) {
            throw new org.springframework.security.access.AccessDeniedException("This job belongs to another employer");
        }

        if (jobDetails.getJobTitle() != null) job.setJobTitle(jobDetails.getJobTitle());
        if (jobDetails.getJobCategory() != null) job.setJobCategory(jobDetails.getJobCategory());
        if (jobDetails.getJobDescription() != null) job.setJobDescription(jobDetails.getJobDescription());
        if (jobDetails.getResponsibilities() != null) job.setResponsibilities(jobDetails.getResponsibilities());
        if (jobDetails.getRequiredSkills() != null) job.setRequiredSkills(jobDetails.getRequiredSkills());
        if (jobDetails.getEducationRequirement() != null) job.setEducationRequirement(jobDetails.getEducationRequirement());
        if (jobDetails.getExperienceRequirement() != null) job.setExperienceRequirement(jobDetails.getExperienceRequirement());
        if (jobDetails.getCertifications() != null) job.setCertifications(jobDetails.getCertifications());
        if (jobDetails.getSalaryMin() != null) job.setSalaryMin(jobDetails.getSalaryMin());
        if (jobDetails.getSalaryMax() != null) job.setSalaryMax(jobDetails.getSalaryMax());
        if (jobDetails.getEmploymentType() != null) job.setEmploymentType(jobDetails.getEmploymentType());
        if (jobDetails.getWorkSchedule() != null) job.setWorkSchedule(jobDetails.getWorkSchedule());
        if (jobDetails.getAvailablePositions() > 0) job.setAvailablePositions(jobDetails.getAvailablePositions());
        if (jobDetails.getMunicipality() != null) job.setMunicipality(jobDetails.getMunicipality());
        if (jobDetails.getBarangay() != null) job.setBarangay(jobDetails.getBarangay());
        if (jobDetails.getJobLocation() != null) job.setJobLocation(jobDetails.getJobLocation());
        job.setActive(jobDetails.isActive());

        return jobPostRepository.save(job);
    }

    @Transactional
    public void deleteJob(Long id, Long employerId) {
        JobPost job = getJobById(id);
        if (employerId == null || !job.getEmployer().getId().equals(employerId)) {
            throw new org.springframework.security.access.AccessDeniedException("This job belongs to another employer");
        }
        jobPostRepository.delete(job);
    }

    public List<JobPost> getJobsByEmployer(Long employerId) {
        return jobPostRepository.findByEmployerId(employerId);
    }

    /**
     * @param seekerUserId the logged-in user's id (not the profile id), or null for anonymous search.
     */
    public Page<JobResponse> searchJobs(JobSearchRequest search, Long seekerUserId) {
        Specification<JobPost> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        boolean sortByMatch = "matchPercentage".equalsIgnoreCase(search.getSortBy()) && seekerUserId != null;
        String sortColumn = SORTABLE_COLUMNS.contains(search.getSortBy()) ? search.getSortBy() : "createdAt";

        Pageable pageable = PageRequest.of(
            Math.max(search.getPage(), 0),
            Math.max(search.getSize(), 1),
            Sort.by(Sort.Direction.fromString(search.getSortDir()), sortColumn)
        );

        Page<JobPost> jobs = jobPostRepository.findAll(spec, pageable);

        Page<JobResponse> page;
        if (seekerUserId != null) {
            JobSeekerProfile seeker = requireSeeker(seekerUserId);
            page = jobs.map(job -> {
                JobResponse response = mapToResponse(job);
                response.setMatchPercentage(matchingEngine.calculateMatch(seeker, job).getOverallMatch());
                return response;
            });
        } else {
            page = jobs.map(this::mapToResponse);
        }

        if (sortByMatch) {
            List<JobResponse> sorted = page.getContent().stream()
                .sorted(Comparator.comparingDouble(
                    (JobResponse response) -> response.getMatchPercentage() == null ? -1.0 : response.getMatchPercentage())
                    .reversed())
                .collect(Collectors.toList());
            return new PageImpl<>(sorted, pageable, page.getTotalElements());
        }
        return page;
    }

    public JobResponse getJobDetails(Long jobId, Long seekerUserId) {
        JobPost job = getJobById(jobId);
        if (!job.isActive()) {
            throw new IllegalArgumentException("Job is not active");
        }
        JobResponse response = mapToResponse(job);
        if (seekerUserId != null) {
            JobSeekerProfile seeker = requireSeeker(seekerUserId);
            response.setMatchPercentage(matchingEngine.calculateMatch(seeker, job).getOverallMatch());
        }
        return response;
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

    @Transactional
    public JobApplication applyForJob(Long seekerUserId, Long jobId) {
        JobSeekerProfile seeker = requireSeeker(seekerUserId);
        JobPost job = jobPostRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        if (!job.isActive()) {
            throw new IllegalArgumentException("Job is not active");
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

        return applicationRepository.save(application);
    }

    /** Resolves the seeker profile from the logged-in user id. */
    private JobSeekerProfile requireSeeker(Long userId) {
        return seekerRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("No job seeker profile for the current account"));
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
        response.setActive(job.isActive());
        response.setCreatedAt(job.getCreatedAt());
        response.setRequiredSkills(job.getRequiredSkills());
        return response;
    }
}
