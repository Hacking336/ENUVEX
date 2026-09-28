package com.jobskills.repository;

import com.jobskills.model.JobApplication;
import com.jobskills.model.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    Optional<JobApplication> findById(Long id);
    List<JobApplication> findByJobSeekerId(Long jobSeekerId);
    List<JobApplication> findByJobPostId(Long jobPostId);
    List<JobApplication> findByApplicationStatus(ApplicationStatus status);
    boolean existsByJobSeekerIdAndJobPostId(Long jobSeekerId, Long jobPostId);
    long countByApplicationStatus(ApplicationStatus status);
}