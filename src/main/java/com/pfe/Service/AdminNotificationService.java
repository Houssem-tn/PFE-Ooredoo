package com.pfe.Service;

import com.pfe.Dto.AdminNotificationDto;
import com.pfe.Entity.AppUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AdminNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public AdminNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyNewPendingUser(AppUser user) {
        AdminNotificationDto dto = new AdminNotificationDto(
                "USER_REGISTERED",
                "New user registration pending approval",
                user.getFullName() + " has registered and is waiting for approval",
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                String.valueOf(user.getStatus()),
                Instant.now()
        );

        messagingTemplate.convertAndSend("/topic/admin/registrations", dto);
    }
}