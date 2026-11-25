package com.jobskills.repository;

import com.jobskills.model.LearningResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {
    List<LearningResource> findByActiveTrueAndSkillKeyOrderByTitleAsc(String skillKey);
    List<LearningResource> findBySkillKeyOrderByTitleAsc(String skillKey);
    List<LearningResource> findAllByOrderByUpdatedAtDescCreatedAtDesc();
    long countBySkillKeyAndActiveTrue(String skillKey);
}
