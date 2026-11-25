package com.jobskills.repository;

import com.jobskills.model.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByCreatedAtAscIdAsc(Long applicationId);

    long countByApplicationId(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
