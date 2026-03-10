package com.pfe.Service;

import com.pfe.Dto.NotificationMessageDto;
import com.pfe.Entity.Notification;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Notification createForRole(UserRole role, NotificationMessageDto payload) {
        if (role == null || payload == null) {
            return null;
        }

        Notification notification = buildRoleNotification(role, payload);
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> createForRoles(List<UserRole> roles, NotificationMessageDto payload) {
        List<Notification> saved = new ArrayList<>();

        if (roles == null || roles.isEmpty() || payload == null) {
            return saved;
        }

        for (UserRole role : roles) {
            Notification notification = createForRole(role, payload);
            if (notification != null) {
                saved.add(notification);
            }
        }

        return saved;
    }

    @Override
    public List<Notification> getNotificationsForRole(UserRole role) {
        return notificationRepository.findByTargetRoleOrderByCreatedAtDesc(role.name());
    }

    @Override
    public long countUnreadForRole(UserRole role) {
        return notificationRepository.countByTargetRoleAndReadFalse(role.name());
    }

    @Override
    public Notification markAsReadForRole(String notificationId, UserRole role) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!role.name().equals(notification.getTargetRole())) {
            throw new IllegalArgumentException("You cannot modify this notification");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }

        return notification;
    }

    @Override
    public List<Notification> markAllAsReadForRole(UserRole role) {
        List<Notification> notifications =
                notificationRepository.findByTargetRoleOrderByCreatedAtDesc(role.name());

        for (Notification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
            }
        }

        return notificationRepository.saveAll(notifications);
    }

    private Notification buildRoleNotification(UserRole role, NotificationMessageDto payload) {
        return Notification.builder()
                .eventType(payload.getEventType())
                .title(payload.getTitle())
                .message(payload.getMessage())
                .entityType(payload.getEntityType())
                .entityId(payload.getEntityId())
                .status(payload.getStatus())
                .targetRole(role.name())
                .recipientEmail(null)
                .triggeredBy(payload.getTriggeredBy())
                .read(false)
                .createdAt(payload.getCreatedAt() != null ? payload.getCreatedAt() : Instant.now())
                .data(payload.getData())
                .build();
    }
}