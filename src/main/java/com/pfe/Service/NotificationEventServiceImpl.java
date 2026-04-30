package com.pfe.Service;

import com.pfe.Dto.NotificationMessageDto;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.Notification;
import com.pfe.Entity.UserRole;
import com.pfe.Notification.NotificationDestinationResolver;
import com.pfe.Notification.NotificationPayloadFactory;
import com.pfe.Notification.WebSocketNotificationSender;
import com.pfe.case_management.entity.CaseEntity;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                && user.getAssignedZmEmail() != null
                && !user.getAssignedZmEmail().isBlank()) {

            Notification saved = notificationService.createForRecipient(user.getAssignedZmEmail(), payload);
            if (saved != null) {
                sender.sendToUser(user.getAssignedZmEmail(), saved);
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

    @Override
    public void onCaseAssignedToZm(CaseEntity caseEntity, String triggeredBy) {
        if (caseEntity == null || isBlank(caseEntity.getZmEmail())) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildCaseAssignedToZm(caseEntity, triggeredBy);
        sendToRecipient(caseEntity.getZmEmail(), payload);
    }

    @Override
    public void onCaseAssignedToStt(CaseEntity caseEntity, String triggeredBy) {
        if (caseEntity == null || isBlank(caseEntity.getSttEmail())) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildCaseAssignedToStt(caseEntity, triggeredBy);
        sendToRecipient(caseEntity.getSttEmail(), payload);
    }

    @Override
    public void onCaseAssignedToTech(CaseEntity caseEntity, String triggeredBy) {
        if (caseEntity == null || isBlank(caseEntity.getTechEmail())) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildCaseAssignedToTech(caseEntity, triggeredBy);
        sendToRecipient(caseEntity.getTechEmail(), payload);
    }

    @Override
    public void onComplaintImportCompleted(String recipientEmail, int processedRows, int importedRows, int skippedRows) {
        if (isBlank(recipientEmail)) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildComplaintImportCompleted(
                recipientEmail.trim().toLowerCase(),
                processedRows,
                importedRows,
                skippedRows
        );
        sendToRecipient(recipientEmail, payload);
    }

    @Override
    public void onSlaWarning(CaseEntity caseEntity) {
        if (caseEntity == null) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildSlaWarning(caseEntity);
        sendToRecipients(payload, caseEntity.getSttEmail(), caseEntity.getTechEmail(), caseEntity.getZmEmail());
    }

    @Override
    public void onSlaBreached(CaseEntity caseEntity) {
        if (caseEntity == null) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildSlaBreached(caseEntity);
        sendToRecipients(payload, caseEntity.getSttEmail(), caseEntity.getTechEmail(), caseEntity.getZmEmail());
    }

    @Override
    public void onSlaEscalated(CaseEntity caseEntity, long breachAgeMinutes) {
        if (caseEntity == null) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildSlaEscalated(caseEntity, breachAgeMinutes);

        sendToRecipients(payload, caseEntity.getSttEmail(), caseEntity.getZmEmail());
        sendToRole(UserRole.ROLE_ADMIN, payload);
    }

    private void sendToRecipients(NotificationMessageDto payload, String... emails) {
        if (emails == null || emails.length == 0) {
            return;
        }

        Set<String> unique = new HashSet<>();

        for (String email : emails) {
            if (isBlank(email)) {
                continue;
            }

            String normalized = email.trim().toLowerCase();
            if (unique.add(normalized)) {
                sendToRecipient(normalized, payload);
            }
        }
    }

    private void sendToRecipient(String email, NotificationMessageDto payload) {
        Notification saved = notificationService.createForRecipient(email, payload);
        if (saved != null) {
            sender.sendToUser(email, saved);
        }
    }

    private void sendToRole(UserRole role, NotificationMessageDto payload) {
        Notification saved = notificationService.createForRole(role, payload);
        if (saved != null) {
            sender.sendToRole(role, saved);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
