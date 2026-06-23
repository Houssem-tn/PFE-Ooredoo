package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Entity.UserRole;

import java.util.List;

public interface NotificationService {

    Notification createForRole(UserRole role, NotificationMessageDto payload);

    List<Notification> createForRoles(List<UserRole> roles, NotificationMessageDto payload);

    Notification createForRecipient(String recipientEmail, NotificationMessageDto payload);

    List<Notification> getNotificationsForRole(UserRole role);

    List<Notification> getNotificationsForRecipient(String recipientEmail);

    long countUnreadForRole(UserRole role);

    long countUnreadForRecipient(String recipientEmail);

    Notification markAsReadForRole(String notificationId, UserRole role);

    Notification markAsReadForRecipient(String notificationId, String recipientEmail);

    List<Notification> markAllAsReadForRole(UserRole role);

    List<Notification> markAllAsReadForRecipient(String recipientEmail);
}