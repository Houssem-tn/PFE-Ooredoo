package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AlertCategory;
import com.pfe.MainPackage.Entity.AlertLevel;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.NotificationRepository;
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
    public Notification createForRecipient(String recipientEmail, NotificationMessageDto payload) {
        if (recipientEmail == null || recipientEmail.isBlank() || payload == null) {
            return null;
        }

        Notification notification = buildRecipientNotification(recipientEmail.trim().toLowerCase(), payload);
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> getNotificationsForRole(UserRole role) {
        return notificationRepository.findByTargetRoleOrderByCreatedAtDesc(role.name());
    }

    @Override
    public List<Notification> getNotificationsForRecipient(String recipientEmail) {
        return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(recipientEmail.trim().toLowerCase());
    }

    @Override
    public long countUnreadForRole(UserRole role) {
        return notificationRepository.countByTargetRoleAndReadFalse(role.name());
    }

    @Override
    public long countUnreadForRecipient(String recipientEmail) {
        return notificationRepository.countByRecipientEmailAndReadFalse(recipientEmail.trim().toLowerCase());
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
    public Notification markAsReadForRecipient(String notificationId, String recipientEmail) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        String normalizedEmail = recipientEmail.trim().toLowerCase();

        if (notification.getRecipientEmail() == null ||
                !normalizedEmail.equals(notification.getRecipientEmail().trim().toLowerCase())) {
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

    @Override
    public List<Notification> markAllAsReadForRecipient(String recipientEmail) {
        List<Notification> notifications =
                notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(recipientEmail.trim().toLowerCase());

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
                .level(resolveLevel(payload.getLevel()))
                .category(resolveCategory(payload.getCategory(), payload.getEventType(), payload.getActionType()))
                .actionType(payload.getActionType())
                .relatedCaseId(payload.getRelatedCaseId())
                .relatedStockRequestId(payload.getRelatedStockRequestId())
                .relatedPermissionRequestId(payload.getRelatedPermissionRequestId())
                .build();
    }

    private Notification buildRecipientNotification(String recipientEmail, NotificationMessageDto payload) {
        return Notification.builder()
                .eventType(payload.getEventType())
                .title(payload.getTitle())
                .message(payload.getMessage())
                .entityType(payload.getEntityType())
                .entityId(payload.getEntityId())
                .status(payload.getStatus())
                .targetRole(null)
                .recipientEmail(recipientEmail)
                .triggeredBy(payload.getTriggeredBy())
                .read(false)
                .createdAt(payload.getCreatedAt() != null ? payload.getCreatedAt() : Instant.now())
                .data(payload.getData())
                .level(resolveLevel(payload.getLevel()))
                .category(resolveCategory(payload.getCategory(), payload.getEventType(), payload.getActionType()))
                .actionType(payload.getActionType())
                .relatedCaseId(payload.getRelatedCaseId())
                .relatedStockRequestId(payload.getRelatedStockRequestId())
                .relatedPermissionRequestId(payload.getRelatedPermissionRequestId())
                .build();
    }

    private AlertLevel resolveLevel(String levelStr) {
        if (levelStr == null) return null;
        try {
            return AlertLevel.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Resolves the business category, falling back to inference from eventType or actionType
     * so that legacy notifications stored without a category still render correctly.
     */
    private AlertCategory resolveCategory(String categoryStr, String eventType, String actionType) {
        if (categoryStr != null) {
            try {
                return AlertCategory.valueOf(categoryStr.toUpperCase());
            } catch (IllegalArgumentException ignored) { }
        }
        // Infer from eventType
        if (eventType != null) {
            if (eventType.startsWith("DPM_") || eventType.startsWith("DPM"))              return AlertCategory.DPM;
            if (eventType.startsWith("SLA_"))                                              return AlertCategory.SLA;
            if (eventType.startsWith("CASE_") || eventType.contains("COMPLAINT"))         return AlertCategory.CASE;
            if (eventType.startsWith("STOCK_USAGE_PERMISSION"))                           return AlertCategory.PERMISSION;
            if (eventType.startsWith("STOCK_"))                                            return AlertCategory.STOCK;
            if (eventType.equals("USER_REGISTERED"))                                       return AlertCategory.USER;
            if (eventType.equals("THRESHOLD_UPDATED"))                                     return AlertCategory.STOCK;
        }
        // Infer from actionType (for any remaining legacy records)
        if (actionType != null) {
            if (actionType.equals("OPEN_CASE"))                                            return AlertCategory.CASE;
            if (actionType.equals("OPEN_DPM") || actionType.equals("OPEN_DPM_REQUEST"))   return AlertCategory.DPM;
            if (actionType.equals("OPEN_STOCK") || actionType.equals("OPEN_STOCK_PAGE"))  return AlertCategory.STOCK;
            if (actionType.equals("OPEN_PERMISSION_REQUEST")
                    || actionType.equals("OPEN_STOCK_PERMISSION_REQUEST"))                 return AlertCategory.PERMISSION;
            if (actionType.equals("OPEN_USER"))                                            return AlertCategory.USER;
            if (actionType.equals("OPEN_THRESHOLDS")
                    || actionType.equals("OPEN_STOCK_THRESHOLDS"))                         return AlertCategory.STOCK;
        }
        return AlertCategory.SYSTEM;
    }
}