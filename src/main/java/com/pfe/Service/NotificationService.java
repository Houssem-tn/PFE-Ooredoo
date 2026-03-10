package com.pfe.Service;

import com.pfe.Dto.NotificationMessageDto;
import com.pfe.Entity.Notification;
import com.pfe.Entity.UserRole;

import java.util.List;

public interface NotificationService {

    Notification createForRole(UserRole role, NotificationMessageDto payload);

    List<Notification> createForRoles(List<UserRole> roles, NotificationMessageDto payload);

    List<Notification> getNotificationsForRole(UserRole role);

    long countUnreadForRole(UserRole role);

    Notification markAsReadForRole(String notificationId, UserRole role);

    List<Notification> markAllAsReadForRole(UserRole role);
}