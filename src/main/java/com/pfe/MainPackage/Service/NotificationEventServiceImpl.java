package com.pfe.MainPackage.Service;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.NotificationRepository;
import com.pfe.Notification.NotificationDestinationResolver;
import com.pfe.Notification.NotificationPayloadFactory;
import com.pfe.Notification.WebSocketNotificationSender;
import com.pfe.case_management.dto.BulkCaseAssignmentSummary;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationEventServiceImpl implements NotificationEventService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventServiceImpl.class);

    private final WebSocketNotificationSender sender;
    private final NotificationPayloadFactory payloadFactory;
    private final NotificationDestinationResolver destinationResolver;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public NotificationEventServiceImpl(WebSocketNotificationSender sender,
                                        NotificationPayloadFactory payloadFactory,
                                        NotificationDestinationResolver destinationResolver,
                                        NotificationService notificationService,
                                        NotificationRepository notificationRepository) {
        this.sender = sender;
        this.payloadFactory = payloadFactory;
        this.destinationResolver = destinationResolver;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void onUserRegistered(AppUser user) {
        if (user == null) {
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildNewPendingRegistration(user);

        if (user.getRoleRequested() == UserRole.ROLE_TECH
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
    public void onCasesBulkAssignedToZm(BulkCaseAssignmentSummary summary) {
        if (summary == null || isBlank(summary.zmEmail()) || summary.count() <= 0) {
            return;
        }

        // Audit trail: one log line per aggregated batch so the operation stays traceable even
        // though only a single notification is now persisted/pushed for the whole batch.
        log.info("[Notification] bulk_case_assignment_aggregate zmEmail={} operation={} caseType={} module={} count={} batchId={} triggeredBy={}",
                summary.zmEmail(), summary.operation(), summary.caseType(), summary.module(),
                summary.count(), summary.batchId(), summary.triggeredBy());

        NotificationMessageDto payload = payloadFactory.buildCasesBulkAssignedToZm(summary);
        sendToRecipient(summary.zmEmail(), payload);
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
    public void onCaseMessageCreated(CaseEntity caseEntity, String recipientEmail, String senderName, String senderRole) {
        if (caseEntity == null || isBlank(recipientEmail)) {
            return;
        }

        String normalized = recipientEmail.trim().toLowerCase();

        // Grouping: one unread "new message" alert per case+recipient is enough. Bursts of
        // chat messages update the badge through the unread-summary endpoint, not the Alert Center.
        boolean alreadyNotified = notificationRepository
                .existsByRecipientEmailAndEventTypeAndEntityIdAndReadFalse(
                        normalized, "CASE_MESSAGE_RECEIVED", caseEntity.getId());
        if (alreadyNotified) {
            log.info("[Notification] case_message_notification_grouped recipient={} caseId={}",
                    normalized, caseEntity.getId());
            return;
        }

        NotificationMessageDto payload = payloadFactory.buildCaseMessageReceived(caseEntity, senderName, senderRole);
        sendToRecipient(normalized, payload);
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

    @Override
    public void onDpmCreated(DpmRequest dpm) {
        if (dpm == null || isBlank(dpm.getSttEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildDpmCreated(dpm);
        sendToRecipient(dpm.getSttEmail(), payload);
    }

    @Override
    public void onDpmConfirmed(DpmRequest dpm) {
        if (dpm == null || isBlank(dpm.getZmEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildDpmConfirmed(dpm);
        sendToRecipient(dpm.getZmEmail(), payload);
    }

    @Override
    public void onDpmRejected(DpmRequest dpm) {
        if (dpm == null || isBlank(dpm.getZmEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildDpmRejected(dpm);
        sendToRecipient(dpm.getZmEmail(), payload);
    }

    @Override
    public void onRdvScheduled(DpmRequest dpm) {
        if (dpm == null || isBlank(dpm.getSttEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildRdvScheduled(dpm);
        sendToRecipient(dpm.getSttEmail(), payload);
    }

    @Override
    public void onRdvConfirmedByStt(DpmRequest dpm) {
        if (dpm == null || isBlank(dpm.getZmEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildRdvConfirmedByStt(dpm);
        sendToRecipient(dpm.getZmEmail(), payload);
    }

    @Override
    public void onExternalRejected(DpmRequest dpm) {
        if (dpm == null || isBlank(dpm.getSttEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildExternalRejected(dpm);
        sendToRecipient(dpm.getSttEmail(), payload);
    }

    @Override
    public void onStockDistributedToTech(SttDistribution distribution) {
        if (distribution == null || isBlank(distribution.getTechEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildStockDistributed(distribution);
        sendToRecipient(distribution.getTechEmail(), payload);
    }

    @Override
    public void onMaterialReturnSubmitted(MaterialReturnRequest returnRequest) {
        if (returnRequest == null || isBlank(returnRequest.getZmEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildMaterialReturnSubmitted(returnRequest);
        sendToRecipient(returnRequest.getZmEmail(), payload);
    }

    @Override
    public void onMaterialReturnRejected(MaterialReturnRequest returnRequest) {
        if (returnRequest == null || isBlank(returnRequest.getSttEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildMaterialReturnRejected(returnRequest);
        sendToRecipient(returnRequest.getSttEmail(), payload);
    }

    @Override
    public void onHandoverScheduleProposed(MaterialReturnRequest returnRequest) {
        if (returnRequest == null || isBlank(returnRequest.getSttEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildHandoverScheduleProposed(returnRequest);
        sendToRecipient(returnRequest.getSttEmail(), payload);
    }

    @Override
    public void onScheduleAcceptedByStt(MaterialReturnRequest returnRequest) {
        if (returnRequest == null || isBlank(returnRequest.getZmEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildScheduleAcceptedByStt(returnRequest);
        sendToRecipient(returnRequest.getZmEmail(), payload);
    }

    @Override
    public void onScheduleRejectedByStt(MaterialReturnRequest returnRequest) {
        if (returnRequest == null || isBlank(returnRequest.getZmEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildScheduleRejectedByStt(returnRequest);
        sendToRecipient(returnRequest.getZmEmail(), payload);
    }

    @Override
    public void onMaterialHandedOver(MaterialReturnRequest returnRequest) {
        if (returnRequest == null || isBlank(returnRequest.getSttEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildMaterialHandedOver(returnRequest);
        sendToRecipient(returnRequest.getSttEmail(), payload);
    }

    @Override
    public void onReturnClosedForZm(MaterialReturnRequest returnRequest) {
        if (returnRequest == null || isBlank(returnRequest.getZmEmail())) return;
        NotificationMessageDto payload = payloadFactory.buildReturnClosedForZm(returnRequest);
        sendToRecipient(returnRequest.getZmEmail(), payload);
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
        log.info("[Notification] sendToRecipient start email={} eventType={} entityId={}",
                email, payload != null ? payload.getEventType() : null, payload != null ? payload.getEntityId() : null);
        Notification saved = notificationService.createForRecipient(email, payload);
        if (saved != null) {
            sender.sendToUser(email, saved);
            log.info("[Notification] sendToRecipient delivered email={} notificationId={}", email, saved.getId());
        } else {
            log.warn("[Notification] sendToRecipient skipped email={} because notification was not created", email);
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
