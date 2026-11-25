package com.jobskills.repository;

import com.jobskills.model.ApplicationInterview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationInterviewRepository extends JpaRepository<ApplicationInterview, Long> {

    Optional<ApplicationInterview> findByApplicationId(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
