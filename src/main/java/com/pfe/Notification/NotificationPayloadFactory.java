package com.pfe.Notification;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
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
                caseLabel(caseEntity) + " " + safe(caseEntity.getCrmCase()) + " has been assigned to you as ZM",
                caseEntity,
                triggeredBy,
                "ROLE_ZM"
        );
    }

    public NotificationMessageDto buildCaseAssignedToStt(CaseEntity caseEntity, String triggeredBy) {
        return buildCasePayload(
                "CASE_ASSIGNED_TO_STT",
                "New case assigned by ZM",
                caseLabel(caseEntity) + " " + safe(caseEntity.getCrmCase()) + " has been assigned to you as STT",
                caseEntity,
                triggeredBy,
                "ROLE_STT"
        );
    }

    public NotificationMessageDto buildCaseAssignedToTech(CaseEntity caseEntity, String triggeredBy) {
        return buildCasePayload(
                "CASE_ASSIGNED_TO_TECH",
                "New case assigned by STT",
                caseLabel(caseEntity) + " " + safe(caseEntity.getCrmCase()) + " has been assigned to you as TECH",
                caseEntity,
                triggeredBy,
                "ROLE_TECH"
        );
    }

    public NotificationMessageDto buildComplaintImportCompleted(
            String triggeredBy,
            int processedRows,
            int importedRows,
            int skippedRows
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("caseType", CaseType.PLAINTE.name());
        data.put("processedRows", processedRows);
        data.put("importedRows", importedRows);
        data.put("skippedRows", skippedRows);
        data.put("routeHint", "/admin/cases/complaint");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("COMPLAINT_IMPORT_COMPLETED");
        dto.setTitle("Plainte import completed");
        dto.setMessage("Plainte import completed: " + processedRows + " rows processed, "
                + importedRows + " imported, " + skippedRows + " skipped.");
        dto.setEntityType("CASE_IMPORT");
        dto.setEntityId(null);
        dto.setStatus("COMPLETED");
        dto.setTriggeredBy(triggeredBy);
        dto.setCreatedAt(Instant.now());
        dto.setData(data);

        return dto;
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
        data.put("routeHint", routeHint(caseEntity));

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

    public NotificationMessageDto buildDpmCreated(DpmRequest dpm) {
        Map<String, Object> data = new HashMap<>();
        data.put("dpmId", dpm.getId());
        data.put("dpmNumber", dpm.getDpmNumber());
        data.put("zmName", dpm.getZmName());
        data.put("itemCount", dpm.getItems() != null ? dpm.getItems().size() : 0);
        data.put("routeHint", "/stt/stock/incoming-dpms");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("DPM_CREATED");
        dto.setTitle("New DPM request received");
        dto.setMessage("DPM " + safe(dpm.getDpmNumber()) + " from ZM " + safe(dpm.getZmName()) + " is awaiting your confirmation");
        dto.setEntityType("DPM");
        dto.setEntityId(dpm.getId());
        dto.setStatus(dpm.getStatus() != null ? dpm.getStatus().name() : null);
        dto.setTriggeredBy(dpm.getZmEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        return dto;
    }

    public NotificationMessageDto buildDpmConfirmed(DpmRequest dpm) {
        Map<String, Object> data = new HashMap<>();
        data.put("dpmId", dpm.getId());
        data.put("dpmNumber", dpm.getDpmNumber());
        data.put("sttName", dpm.getSttName());
        data.put("status", dpm.getStatus() != null ? dpm.getStatus().name() : null);
        data.put("routeHint", "/zm/stock/my-tracking");

        String statusLabel = dpm.getStatus() != null && dpm.getStatus().name().contains("PARTIAL")
                ? "partially confirmed" : "fully confirmed";

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("DPM_CONFIRMED");
        dto.setTitle("DPM " + statusLabel);
        dto.setMessage("DPM " + safe(dpm.getDpmNumber()) + " has been " + statusLabel + " by STT " + safe(dpm.getSttName()));
        dto.setEntityType("DPM");
        dto.setEntityId(dpm.getId());
        dto.setStatus(dpm.getStatus() != null ? dpm.getStatus().name() : null);
        dto.setTriggeredBy(dpm.getSttEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        return dto;
    }

    public NotificationMessageDto buildDpmRejected(DpmRequest dpm) {
        Map<String, Object> data = new HashMap<>();
        data.put("dpmId", dpm.getId());
        data.put("dpmNumber", dpm.getDpmNumber());
        data.put("sttName", dpm.getSttName());
        data.put("routeHint", "/zm/stock/my-tracking");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("DPM_REJECTED");
        dto.setTitle("DPM rejected");
        dto.setMessage("DPM " + safe(dpm.getDpmNumber()) + " has been rejected by STT " + safe(dpm.getSttName()));
        dto.setEntityType("DPM");
        dto.setEntityId(dpm.getId());
        dto.setStatus("REJECTED");
        dto.setTriggeredBy(dpm.getSttEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        return dto;
    }

    public NotificationMessageDto buildRdvScheduled(DpmRequest dpm) {
        Map<String, Object> data = new HashMap<>();
        data.put("dpmId", dpm.getId());
        data.put("dpmNumber", dpm.getDpmNumber());
        data.put("zmName", dpm.getZmName());
        data.put("rdvDate", dpm.getRdvDate());
        data.put("rdvLocation", dpm.getRdvLocation());
        data.put("rdvNote", dpm.getRdvNote());
        data.put("routeHint", "/stt/stock/incoming-dpms");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("DPM_RDV_SCHEDULED");
        dto.setTitle("RDV scheduled for DPM " + safe(dpm.getDpmNumber()));
        dto.setMessage("DPM " + safe(dpm.getDpmNumber()) + " has been approved externally. "
                + "Pickup RDV: " + safe(dpm.getRdvDate()) + " at " + safe(dpm.getRdvLocation()));
        dto.setEntityType("DPM");
        dto.setEntityId(dpm.getId());
        dto.setStatus(dpm.getStatus() != null ? dpm.getStatus().name() : null);
        dto.setTriggeredBy(dpm.getZmEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        return dto;
    }

    public NotificationMessageDto buildRdvConfirmedByStt(DpmRequest dpm) {
        Map<String, Object> data = new HashMap<>();
        data.put("dpmId", dpm.getId());
        data.put("dpmNumber", dpm.getDpmNumber());
        data.put("sttName", dpm.getSttName());
        data.put("routeHint", "/zm/stock/my-tracking");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("DPM_RDV_CONFIRMED_BY_STT");
        dto.setTitle("STT confirmed the RDV for DPM " + safe(dpm.getDpmNumber()));
        dto.setMessage("STT " + safe(dpm.getSttName()) + " confirmed attendance for DPM " + safe(dpm.getDpmNumber()));
        dto.setEntityType("DPM");
        dto.setEntityId(dpm.getId());
        dto.setStatus(dpm.getStatus() != null ? dpm.getStatus().name() : null);
        dto.setTriggeredBy(dpm.getSttEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        return dto;
    }

    public NotificationMessageDto buildExternalRejected(DpmRequest dpm) {
        Map<String, Object> data = new HashMap<>();
        data.put("dpmId", dpm.getId());
        data.put("dpmNumber", dpm.getDpmNumber());
        data.put("zmName", dpm.getZmName());
        data.put("rejectionReason", dpm.getExternalRejectionReason());
        data.put("routeHint", "/stt/stock/incoming-dpms");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("DPM_EXTERNAL_REJECTED");
        dto.setTitle("DPM " + safe(dpm.getDpmNumber()) + " rejected by Team Stock");
        dto.setMessage("DPM " + safe(dpm.getDpmNumber()) + " was rejected by the external Team Stock."
                + (dpm.getExternalRejectionReason() != null ? " Reason: " + dpm.getExternalRejectionReason() : ""));
        dto.setEntityType("DPM");
        dto.setEntityId(dpm.getId());
        dto.setStatus("TEAM_STOCK_REJECTED");
        dto.setTriggeredBy(dpm.getZmEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        return dto;
    }

    public NotificationMessageDto buildStockDistributed(SttDistribution distribution) {
        Map<String, Object> data = new HashMap<>();
        data.put("distributionId", distribution.getId());
        data.put("sttId", distribution.getSttId());
        data.put("itemCount", distribution.getItems() != null ? distribution.getItems().size() : 0);
        data.put("routeHint", "/tech/stock/my-stock");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("STOCK_DISTRIBUTED");
        dto.setTitle("Stock received from STT");
        dto.setMessage("Equipment has been distributed to you by your STT");
        dto.setEntityType("DISTRIBUTION");
        dto.setEntityId(distribution.getId());
        dto.setStatus("DISTRIBUTED");
        dto.setTriggeredBy(distribution.getDistributedBy());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        return dto;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String caseLabel(CaseEntity caseEntity) {
        if (caseEntity != null && caseEntity.getType() == CaseType.PLAINTE) {
            return "Complaint case";
        }

        if (caseEntity != null && caseEntity.getType() == CaseType.RESILIATION) {
            return "Resiliation case";
        }

        return "Activation case";
    }

    private String routeHint(CaseEntity caseEntity) {
        if (caseEntity != null && caseEntity.getType() == CaseType.PLAINTE) {
            return "/cases/complaint";
        }

        return "/cases/activation";
    }
}
