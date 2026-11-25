package com.jobskills.repository;

import com.jobskills.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, boolean isRead);

    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);

    long countByUserIdAndIsReadFalse(Long userId);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    java.util.Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update Notification n set n.isRead = true, n.readAt = :now "
        + "where n.user.id = :userId and n.isRead = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from Notification n where n.id = :id and n.user.id = :userId")
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Inserts a notification unless this recipient already has one with the same
     * dedupe key. The unique index does the check atomically, so a repeated event
     * (double click, retried request) cannot create a second row.
     */
    @Modifying
    @Query(value = "INSERT INTO notifications "
        + "(user_id, message, title, type, link, reference_type, reference_id, is_read, dedupe_key, created_at) "
        + "VALUES (:userId, :message, :title, :type, :link, :referenceType, :referenceId, false, :dedupeKey, "
        + "CURRENT_TIMESTAMP) "
        + "ON CONFLICT (user_id, dedupe_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("message") String message,
                       @Param("title") String title,
                       @Param("type") String type,
                       @Param("link") String link,
                       @Param("referenceType") String referenceType,
                       @Param("referenceId") Long referenceId,
                       @Param("dedupeKey") String dedupeKey);
}
