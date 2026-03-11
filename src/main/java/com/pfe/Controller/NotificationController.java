package com.pfe.Controller;

import com.pfe.Entity.Notification;
import com.pfe.Entity.UserRole;
import com.pfe.Service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/my-role")
    public ResponseEntity<List<Notification>> getMyNotifications(Authentication authentication) {
        UserRole role = extractRole(authentication);
        String email = authentication.getName();

        List<Notification> roleNotifications = notificationService.getNotificationsForRole(role);
        List<Notification> directNotifications = notificationService.getNotificationsForRecipient(email);

        List<Notification> merged = Stream.concat(roleNotifications.stream(), directNotifications.stream())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .toList();

        return ResponseEntity.ok(merged);
    }

    @GetMapping("/my-role/unread-count")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication) {
        UserRole role = extractRole(authentication);
        String email = authentication.getName();

        long total = notificationService.countUnreadForRole(role)
                + notificationService.countUnreadForRecipient(email);

        return ResponseEntity.ok(total);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable String id, Authentication authentication) {
        UserRole role = extractRole(authentication);
        String email = authentication.getName();

        try {
            return ResponseEntity.ok(notificationService.markAsReadForRole(id, role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(notificationService.markAsReadForRecipient(id, email));
        }
    }

    @PatchMapping("/my-role/read-all")
    public ResponseEntity<List<Notification>> markAllAsRead(Authentication authentication) {
        UserRole role = extractRole(authentication);
        String email = authentication.getName();

        List<Notification> roleUpdated = notificationService.markAllAsReadForRole(role);
        List<Notification> directUpdated = notificationService.markAllAsReadForRecipient(email);

        List<Notification> merged = Stream.concat(roleUpdated.stream(), directUpdated.stream())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .toList();

        return ResponseEntity.ok(merged);
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