package com.pfe.Controller;

import com.pfe.Entity.Notification;
import com.pfe.Entity.UserRole;
import com.pfe.Service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/my-role")
    public ResponseEntity<List<Notification>> getMyRoleNotifications(Authentication authentication) {
        UserRole role = extractRole(authentication);
        return ResponseEntity.ok(notificationService.getNotificationsForRole(role));
    }

    @GetMapping("/my-role/unread-count")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication) {
        UserRole role = extractRole(authentication);
        return ResponseEntity.ok(notificationService.countUnreadForRole(role));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable String id, Authentication authentication) {
        UserRole role = extractRole(authentication);
        return ResponseEntity.ok(notificationService.markAsReadForRole(id, role));
    }

    @PatchMapping("/my-role/read-all")
    public ResponseEntity<List<Notification>> markAllAsRead(Authentication authentication) {
        UserRole role = extractRole(authentication);
        return ResponseEntity.ok(notificationService.markAllAsReadForRole(role));
    }

    private UserRole extractRole(Authentication authentication) {
        String authority = authentication.getAuthorities()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No role found"))
                .getAuthority();

        return UserRole.valueOf(authority);
    }
}