package com.jobskills.repository;

import com.jobskills.model.SkillLearningInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillLearningInterestRepository extends JpaRepository<SkillLearningInterest, Long> {
    List<SkillLearningInterest> findBySeekerIdOrderByCreatedAtDesc(Long seekerProfileId);
    Optional<SkillLearningInterest> findBySeekerIdAndSkillKey(Long seekerProfileId, String skillKey);
    void deleteBySeekerIdAndSkillKey(Long seekerProfileId, String skillKey);
}
