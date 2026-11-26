package com.jobskills.repository;

import com.jobskills.model.SuspiciousListing;
import com.jobskills.model.enums.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

/** Repository for SuspiciousListing entities. */
public interface SuspiciousListingRepository extends JpaRepository<SuspiciousListing, Long> {

    /** Find listings by status. */
    List<SuspiciousListing> findByStatus(ModerationStatus status);

    /** Find dismissed listings. */
    List<SuspiciousListing> findByDismissedTrueOrderByFlaggedAtDesc();

    /** Find active (non-dismissed) listings ordered by flag date. */
    @Query("SELECT sl FROM SuspiciousListing sl WHERE sl.dismissed = false ORDER BY sl.flaggedAt DESC")
    List<SuspiciousListing> findActiveByFlaggedAtDesc();

    /** Find all listings ordered by flagged at descending. */
    List<SuspiciousListing> findAllByOrderByFlaggedAtDesc();

    /** Find listings by triggered rule. */
    List<SuspiciousListing> findByTriggeredRule(String rule);

    /** True when this rule already has a live (not dismissed) flag on a listing. */
    boolean existsByJobPostIdAndTriggeredRuleAndDismissedFalse(Long jobPostId, String triggeredRule);

    /** Moderation history for one listing, newest first. */
    List<SuspiciousListing> findByJobPostIdOrderByFlaggedAtDesc(Long jobPostId);

    /** Count by status. */
    @Query("SELECT sl.status, COUNT(sl) FROM SuspiciousListing sl GROUP BY sl.status")
    List<Object[]> countByStatus();
}