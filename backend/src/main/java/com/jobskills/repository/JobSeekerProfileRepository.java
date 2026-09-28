package com.jobskills.repository;

import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, Long> {
    Optional<JobSeekerProfile> findByUserId(Long userId);
    Optional<JobSeekerProfile> findByUser_Email(String email);
    List<JobSeekerProfile> findByMunicipality(Municipality municipality);
}