package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.NotificationDto;
import com.jobskills.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * The signed-in user's notifications, newest first. The query parameters keep
     * the endpoint usable by the full inbox page without changing the shape the
     * bell already consumes.
     */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String type,
            Authentication authentication) {
        return ResponseEntity.ok(notificationService.list(extractUserId(authentication), filter, type));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(extractUserId(authentication))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id, Authentication authentication) {
        boolean updated = notificationService.markRead(extractUserId(authentication), id);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", "read", "unread",
            notificationService.unreadCount(extractUserId(authentication))));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        Long userId = extractUserId(authentication);
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("updated", updated, "unread", notificationService.unreadCount(userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id, Authentication authentication) {
        boolean deleted = notificationService.delete(extractUserId(authentication), id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }
}
