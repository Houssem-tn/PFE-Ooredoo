package com.pfe.Service;

import com.pfe.Dto.NotificationMessageDto;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.Notification;
import com.pfe.Entity.UserRole;
import com.pfe.Notification.NotificationDestinationResolver;
import com.pfe.Notification.NotificationPayloadFactory;
import com.pfe.Notification.WebSocketNotificationSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationEventServiceImpl implements NotificationEventService {

    private final WebSocketNotificationSender sender;
    private final NotificationPayloadFactory payloadFactory;
    private final NotificationDestinationResolver destinationResolver;
    private final NotificationService notificationService;

    public NotificationEventServiceImpl(WebSocketNotificationSender sender,
                                        NotificationPayloadFactory payloadFactory,
                                        NotificationDestinationResolver destinationResolver,
                                        NotificationService notificationService) {
        this.sender = sender;
        this.payloadFactory = payloadFactory;
        this.destinationResolver = destinationResolver;
        this.notificationService = notificationService;
    }

    @Override
    public void onUserRegistered(AppUser user) {
        NotificationMessageDto payload = payloadFactory.buildNewPendingRegistration(user);

        if (user != null
                && user.getRoleRequested() == UserRole.ROLE_TECH
                && user.getAssignedSttEmail() != null
                && !user.getAssignedSttEmail().isBlank()) {

            Notification saved = notificationService.createForRecipient(user.getAssignedSttEmail(), payload);
            if (saved != null) {
                sender.sendToUser(user.getAssignedSttEmail(), saved);
            }

            return;
        }

        List<UserRole> targets = destinationResolver.resolveRolesForNewRegistration(user);
        List<Notification> savedNotifications = notificationService.createForRoles(targets, payload);

        for (Notification notification : savedNotifications) {
            UserRole role = UserRole.valueOf(notification.getTargetRole());
            sender.sendToRole(role, notification);
        }
    }
}