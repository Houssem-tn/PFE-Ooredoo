package com.pfe.Notification;

import com.pfe.Dto.NotificationMessageDto;
import com.pfe.Entity.AppUser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationPayloadFactory {

    public NotificationMessageDto buildNewPendingRegistration(AppUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("fullName", user.getFullName());
        data.put("email", user.getEmail());
        data.put("roleRequested", user.getRoleRequested() != null ? user.getRoleRequested().name() : null);
        data.put("status", user.getStatus() != null ? user.getStatus().name() : null);
        data.put("enabled", user.isEnabled());

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("USER_REGISTERED");
        dto.setTitle("New user registration pending approval");
        dto.setMessage(user.getFullName() + " has registered and is waiting for approval");
        dto.setEntityType("USER");
        dto.setEntityId(user.getId());
        dto.setStatus(user.getStatus() != null ? user.getStatus().name() : null);
        dto.setTriggeredBy(user.getEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);

        return dto;
    }
}