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
        job.setBarangay(blankToNull(request.getBarangay()));
        job.setJobLocation(blankToNull(request.getJobLocation()));
        applySalary(job, request);
        job.setAvailablePositions(request.getAvailablePositions() == null || request.getAvailablePositions() < 1
            ? 1 : request.getAvailablePositions());
        // A new post is live unless the employer explicitly saves it paused;
        // an inactive post never shows up in a seeker's search.
        job.setActive(request.getActive() == null || request.getActive());

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
        if (request.getMunicipality() != null) job.setMunicipality(request.getMunicipality());
        if (request.getBarangay() != null) job.setBarangay(blankToNull(request.getBarangay()));
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
                JobResponse jobResponse = mapToResponse(job);
                jobResponse.setMatchPercentage(matchingEngine.calculateMatch(seeker, job).getOverallMatch());
                response.setJob(jobResponse);
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
