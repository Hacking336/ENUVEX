package com.jobskills.repository;

import com.jobskills.model.JobPost;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobPostRepository extends JpaRepository<JobPost, Long> {
    Optional<JobPost> findById(Long id);
    List<JobPost> findByIsActiveTrue();
    List<JobPost> findByMunicipality(Municipality municipality);
    List<JobPost> findByJobCategory(JobCategory category);
    List<JobPost> findByEmployerId(Long employerId);
    long countByIsActiveTrue();
    long countByMunicipality(Municipality municipality);
    long countByJobCategory(JobCategory category);
}