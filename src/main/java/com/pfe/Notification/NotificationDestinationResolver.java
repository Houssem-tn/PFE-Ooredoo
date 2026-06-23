package com.pfe.Notification;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationDestinationResolver {

    public List<UserRole> resolveRolesForNewRegistration(AppUser user) {
        if (user == null || user.getRoleRequested() == null) {
            return List.of(UserRole.ROLE_ADMIN);
        }

        return switch (user.getRoleRequested()) {
            case ROLE_ZM -> List.of(UserRole.ROLE_ADMIN);
            case ROLE_STT -> List.of(UserRole.ROLE_ZM, UserRole.ROLE_ADMIN);
            case ROLE_TECH -> List.of(); // handled as user-targeted notification to selected STT
            default -> List.of(UserRole.ROLE_ADMIN);
        };
    }
}