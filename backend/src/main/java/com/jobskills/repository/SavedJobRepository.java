package com.jobskills.repository;

import com.jobskills.model.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    Optional<SavedJob> findByJobSeekerIdAndJobPostId(Long jobSeekerId, Long jobPostId);
    List<SavedJob> findByJobSeekerId(Long jobSeekerId);
    void deleteByJobSeekerIdAndJobPostId(Long jobSeekerId, Long jobPostId);
}