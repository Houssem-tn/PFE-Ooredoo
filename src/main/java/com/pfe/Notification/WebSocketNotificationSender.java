package com.pfe.Notification;

import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Entity.UserRole;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketNotificationSender {

    private static final String ROLE_TOPIC_PREFIX = "/topic/notifications/";
    private static final String USER_TOPIC_PREFIX = "/topic/notifications/user/";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationSender(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendToRole(UserRole role, Notification notification) {
        if (role == null || notification == null) {
            return;
        }

        messagingTemplate.convertAndSend(ROLE_TOPIC_PREFIX + role.name(), notification);
    }

    public void sendToUser(String email, Notification notification) {
        if (email == null || email.isBlank() || notification == null) {
            return;
        }

        messagingTemplate.convertAndSend(USER_TOPIC_PREFIX + email.trim().toLowerCase(), notification);
    }
}