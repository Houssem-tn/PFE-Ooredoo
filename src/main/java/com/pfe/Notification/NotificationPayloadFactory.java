package com.pfe.Notification;

import com.pfe.Dto.NotificationMessageDto;
import com.pfe.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.model.SlaTrackingInfo;
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

    public NotificationMessageDto buildCaseAssignedToZm(CaseEntity caseEntity, String triggeredBy) {
        return buildCasePayload(
                "CASE_ASSIGNED_TO_ZM",
                "New case assigned by Admin",
                "Activation case " + safe(caseEntity.getCrmCase()) + " has been assigned to you as ZM",
                caseEntity,
                triggeredBy,
                "ROLE_ZM"
        );
    }

    public NotificationMessageDto buildCaseAssignedToStt(CaseEntity caseEntity, String triggeredBy) {
        return buildCasePayload(
                "CASE_ASSIGNED_TO_STT",
                "New case assigned by ZM",
                "Activation case " + safe(caseEntity.getCrmCase()) + " has been assigned to you as STT",
                caseEntity,
                triggeredBy,
                "ROLE_STT"
        );
    }

    public NotificationMessageDto buildCaseAssignedToTech(CaseEntity caseEntity, String triggeredBy) {
        return buildCasePayload(
                "CASE_ASSIGNED_TO_TECH",
                "New case assigned by STT",
                "Activation case " + safe(caseEntity.getCrmCase()) + " has been assigned to you as TECH",
                caseEntity,
                triggeredBy,
                "ROLE_TECH"
        );
    }

    public NotificationMessageDto buildSlaWarning(CaseEntity caseEntity) {
        String warningMessage = "Case " + safe(caseEntity.getCrmCase()) + " is nearing the RDV SLA deadline";
        SlaTrackingInfo currentSla = caseEntity.getSlaTracking();
        if (currentSla != null && currentSla.getWarningThresholdPercent() != null) {
            warningMessage = "Case " + safe(caseEntity.getCrmCase()) + " entered warning zone at "
                + Math.round(currentSla.getWarningThresholdPercent()) + "% remaining SLA";
        }

        NotificationMessageDto dto = buildCasePayload(
                "SLA_WARNING",
                "SLA warning: RDV deadline approaching",
            warningMessage,
                caseEntity,
                "SYSTEM",
                null
        );

        SlaTrackingInfo sla = currentSla;
        if (sla != null) {
            dto.getData().put("slaStatus", sla.getStatus() != null ? sla.getStatus().name() : null);
            dto.getData().put("remainingSeconds", sla.getRemainingSeconds());
            dto.getData().put("progressPercent", sla.getProgressPercent());
            dto.getData().put("deadlineAt", sla.getDeadlineAt());
        }

        return dto;
    }

    public NotificationMessageDto buildSlaBreached(CaseEntity caseEntity) {
        NotificationMessageDto dto = buildCasePayload(
                "SLA_BREACHED",
                "SLA breached: RDV deadline missed",
                "Case " + safe(caseEntity.getCrmCase()) + " has breached the RDV SLA",
                caseEntity,
                "SYSTEM",
                null
        );

        SlaTrackingInfo sla = caseEntity.getSlaTracking();
        if (sla != null) {
            dto.getData().put("slaStatus", sla.getStatus() != null ? sla.getStatus().name() : null);
            dto.getData().put("breachedAt", sla.getBreachedAt());
            dto.getData().put("responseDurationSeconds", sla.getResponseDurationSeconds());
            dto.getData().put("progressPercent", sla.getProgressPercent());
        }

        return dto;
    }

    public NotificationMessageDto buildSlaEscalated(CaseEntity caseEntity, long breachAgeMinutes) {
        NotificationMessageDto dto = buildCasePayload(
                "SLA_ESCALATED",
                "SLA escalation: management attention required",
                "Case " + safe(caseEntity.getCrmCase()) + " remained breached for " + breachAgeMinutes + " minute(s)",
                caseEntity,
                "SYSTEM",
                null
        );

        SlaTrackingInfo sla = caseEntity.getSlaTracking();
        dto.getData().put("breachAgeMinutes", breachAgeMinutes);

        if (sla != null) {
            dto.getData().put("slaStatus", sla.getStatus() != null ? sla.getStatus().name() : null);
            dto.getData().put("breachedAt", sla.getBreachedAt());
            dto.getData().put("escalationTriggeredAt", sla.getEscalationTriggeredAt());
            dto.getData().put("remainingSeconds", sla.getRemainingSeconds());
        }

        return dto;
    }

    private NotificationMessageDto buildCasePayload(
            String eventType,
            String title,
            String message,
            CaseEntity caseEntity,
            String triggeredBy,
            String assigneeRole
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("caseId", caseEntity.getId());
        data.put("crmCase", caseEntity.getCrmCase());
        data.put("caseType", caseEntity.getType() != null ? caseEntity.getType().name() : null);
        data.put("caseStatus", caseEntity.getStatus() != null ? caseEntity.getStatus().name() : null);
        data.put("assigneeRole", assigneeRole);
        data.put("executorRole", caseEntity.getExecutorRole() != null ? caseEntity.getExecutorRole().name() : null);
        data.put("executionMode", caseEntity.getExecutionMode() != null ? caseEntity.getExecutionMode().name() : null);
        data.put("routeHint", "/cases/activation");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType(eventType);
        dto.setTitle(title);
        dto.setMessage(message);
        dto.setEntityType("CASE");
        dto.setEntityId(caseEntity.getId());
        dto.setStatus(caseEntity.getStatus() != null ? caseEntity.getStatus().name() : null);
        dto.setTriggeredBy(triggeredBy);
        dto.setCreatedAt(Instant.now());
        dto.setData(data);

        return dto;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
