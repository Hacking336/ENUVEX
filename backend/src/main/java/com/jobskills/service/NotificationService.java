package com.jobskills.service;

import com.jobskills.dto.NotificationDto;
import com.jobskills.model.Notification;
import com.jobskills.model.User;
import com.jobskills.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Persistent in-app notifications.
 *
 * Notifications are only written from real events (an application was submitted,
 * a status changed, an interview was scheduled, a new job matches a profile).
 * Every event supplies a dedupe key, and the insert is ON CONFLICT DO NOTHING,
 * so re-running an event can never produce a second row for the same recipient.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED";
    public static final String APPLICATION_STATUS = "APPLICATION_STATUS";
    public static final String APPLICATION_WITHDRAWN = "APPLICATION_WITHDRAWN";
    public static final String APPLICATION_RECEIVED = "APPLICATION_RECEIVED";
    public static final String APPLICANT_WITHDRAWN = "APPLICANT_WITHDRAWN";
    public static final String INTERVIEW_INVITE = "INTERVIEW_INVITE";
    public static final String INTERVIEW_RESCHEDULED = "INTERVIEW_RESCHEDULED";
    public static final String INTERVIEW_CANCELLED = "INTERVIEW_CANCELLED";
    public static final String JOB_RECOMMENDATION = "JOB_RECOMMENDATION";
    public static final String EMPLOYER_VERIFICATION = "EMPLOYER_VERIFICATION";
    public static final String JOB_POST_APPROVED = "JOB_POST_APPROVED";
    public static final String JOB_POST_REJECTED = "JOB_POST_REJECTED";
    public static final String JOB_POST_CHANGES_REQUESTED = "JOB_POST_CHANGES_REQUESTED";
    public static final String EMPLOYER_VERIFIED = "EMPLOYER_VERIFIED";
    public static final String EMPLOYER_REJECTED = "EMPLOYER_REJECTED";
    public static final String VERIFICATION_INFO_REQUESTED = "VERIFICATION_INFO_REQUESTED";
    public static final String REPORT_RESOLVED = "REPORT_RESOLVED";
    public static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";
    public static final String ACCOUNT_RESTORED = "ACCOUNT_RESTORED";
    public static final String SUSPICIOUS_LISTING_FLAGGED = "SUSPICIOUS_LISTING_FLAGGED";

    private final NotificationRepository notificationRepository;

    @Transactional
    public boolean notify(User recipient, String type, String title, String message,
                          String link, String referenceType, Long referenceId, String dedupeKey) {
        if (recipient == null || recipient.getId() == null) {
            return false;
        }
        int inserted = notificationRepository.insertIfAbsent(
            recipient.getId(), message, title, type, link, referenceType, referenceId, dedupeKey);
        return inserted > 0;
    }

    public List<NotificationDto> list(Long userId, String filter, String type) {
        boolean unreadOnly = "unread".equalsIgnoreCase(filter);
        boolean readOnly = "read".equalsIgnoreCase(filter);

        List<Notification> items;
        if (type != null && !type.isBlank()) {
            items = notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                userId, type.trim().toUpperCase(Locale.ROOT));
        } else if (unreadOnly) {
            items = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false);
        } else if (readOnly) {
            items = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, true);
        } else {
            items = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return items.stream().map(this::toDto).collect(Collectors.toList());
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /** Marks one notification read. Returns false when it is not this user's. */
    @Transactional
    public boolean markRead(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
            .map(notification -> {
                if (!notification.isRead()) {
                    notification.setRead(true);
                    notification.setReadAt(LocalDateTime.now());
                    notificationRepository.save(notification);
                }
                return true;
            })
            .orElse(false);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    @Transactional
    public boolean delete(Long userId, Long notificationId) {
        return notificationRepository.deleteOwned(notificationId, userId) > 0;
    }

    private NotificationDto toDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setType(notification.getType());
        dto.setLink(notification.getLink());
        dto.setReferenceType(notification.getReferenceType());
        dto.setReferenceId(notification.getReferenceId());
        dto.setRead(notification.isRead());
        dto.setReadAt(notification.getReadAt());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}
