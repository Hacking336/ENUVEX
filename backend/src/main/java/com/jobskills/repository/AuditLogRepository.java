package com.jobskills.repository;

import com.jobskills.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByActionTypeContainingOrderByCreatedAtDesc(String actionType);

    List<AuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<AuditLog> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    Optional<AuditLog> findById(Long id);
}