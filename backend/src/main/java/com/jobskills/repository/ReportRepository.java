package com.jobskills.repository;

import com.jobskills.model.Report;
import com.jobskills.model.enums.ReportCategory;
import com.jobskills.model.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Repository for Report entities with query methods for moderation. */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Find open reports. */
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    /** Find reports by category. */
    List<Report> findByCategoryOrderByCreatedAtDesc(ReportCategory category);

    /** Find reports by category. */
    List<Report> findByCategory(ReportCategory category);

    /** Find reports by target type and ID. */
    List<Report> findByReportedTargetTypeAndReportedTargetIdOrderByCreatedAtDesc(
            String targetType, Long targetId);

    /** Find reports by reporter. */
    List<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    /** Find duplicate reports. */
    List<Report> findByIsDuplicateTrueOrderByCreatedAtDesc();

    /** Find resolved reports. */
    List<Report> findByStatusInOrderByCreatedAtDesc(List<ReportStatus> statuses);

    /** Find all reports ordered by created at descending. */
    List<Report> findAllByOrderByCreatedAtDesc();

    /** True when this user already reported this exact target. */
    boolean existsByReporterIdAndReportedTargetTypeAndReportedTargetId(
            Long reporterId, String reportedTargetType, Long reportedTargetId);
}