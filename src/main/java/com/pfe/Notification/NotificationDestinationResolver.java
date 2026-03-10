package com.pfe.Notification;

import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationDestinationResolver {

    /**
     * Registration workflow notifications
     */
    public List<UserRole> resolveRolesForNewRegistration(AppUser user) {
        if (user == null || user.getRoleRequested() == null) {
            return List.of(UserRole.ROLE_ADMIN);
        }

        return switch (user.getRoleRequested()) {
            case ROLE_ZM -> List.of(UserRole.ROLE_ADMIN);
            case ROLE_STT -> List.of(UserRole.ROLE_ADMIN, UserRole.ROLE_ZM);
            case ROLE_TECH -> List.of(UserRole.ROLE_ADMIN, UserRole.ROLE_STT,UserRole.ROLE_ZM);
            default -> List.of(UserRole.ROLE_ADMIN);
        };
    }

    /**
     * Future examples:
     * add one method per business event type
     */

    // public List<UserRole> resolveRolesForApprovalAccepted(AppUser user) { ... }

    // public List<UserRole> resolveRolesForApprovalRejected(AppUser user) { ... }

    // public List<UserRole> resolveRolesForInterventionAssigned(Intervention intervention) { ... }

    // public List<UserRole> resolveRolesForStockAlert(StockItem stockItem) { ... }
}