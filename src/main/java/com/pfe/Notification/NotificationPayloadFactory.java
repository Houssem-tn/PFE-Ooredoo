package com.pfe.Notification;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.case_management.dto.BulkCaseAssignmentSummary;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.model.BulkCaseOperationType;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationPayloadFactory {

    /** Human-friendly operation date used in aggregated bulk summaries, e.g. "11/06/2026". */
    private static final DateTimeFormatter OPERATION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

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
        dto.setLevel("INFO");
        dto.setCategory("USER");
        dto.setActionType("OPEN_USER");
        return dto;
    }

    public NotificationMessageDto buildCaseAssignedToZm(CaseEntity caseEntity, String triggeredBy) {
        NotificationMessageDto dto = buildCasePayload(
                "CASE_ASSIGNED_TO_ZM",
                "New case assigned by Admin",
                caseLabel(caseEntity) + " " + safe(caseEntity.getCrmCase()) + " has been assigned to you as ZM",
                caseEntity,
                triggeredBy,
                "ROLE_ZM"
        );
        dto.setLevel("INFO");
        dto.setCategory("CASE");
        dto.setActionType("OPEN_CASE");
        return dto;
    }

    public NotificationMessageDto buildCaseAssignedToStt(CaseEntity caseEntity, String triggeredBy) {
        NotificationMessageDto dto = buildCasePayload(
                "CASE_ASSIGNED_TO_STT",
                "New case assigned by ZM",
                caseLabel(caseEntity) + " " + safe(caseEntity.getCrmCase()) + " has been assigned to you as STT",
                caseEntity,
                triggeredBy,
                "ROLE_STT"
        );
        dto.setLevel("INFO");
        dto.setCategory("CASE");
        dto.setActionType("OPEN_CASE");
        return dto;
    }

    public NotificationMessageDto buildCaseAssignedToTech(CaseEntity caseEntity, String triggeredBy) {
        NotificationMessageDto dto = buildCasePayload(
                "CASE_ASSIGNED_TO_TECH",
                "New case assigned by STT",
                caseLabel(caseEntity) + " " + safe(caseEntity.getCrmCase()) + " has been assigned to you as TECH",
                caseEntity,
                triggeredBy,
                "ROLE_TECH"
        );
        dto.setLevel("INFO");
        dto.setCategory("CASE");
        dto.setActionType("OPEN_CASE");
        return dto;
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
        dto.setLevel("INFO");
        dto.setCategory("CASE");
        dto.setActionType("OPEN_CASE");
        return dto;
    }

    /**
     * Builds a single aggregated notification for a bulk case-assignment batch.
     * Replaces the previous one-notification-per-case behaviour: instead of hundreds of alerts,
     * the ZM receives one concise summary carrying the count, type, responsible actor, timestamp
     * and a deep link to the detailed case list for the batch.
     *
     * <p>Example message: <em>"50 complaint cases injected by ZM Issam Aissa on 11/06/2026 —
     * ready to work."</em></p>
     */
    public NotificationMessageDto buildCasesBulkAssignedToZm(BulkCaseAssignmentSummary summary) {
        int count = Math.max(0, summary.count());
        String caseLabel = bulkCaseTypeLabel(summary.caseType(), summary.module());
        String plural = count == 1 ? "case" : "cases";
        String operationDate = OPERATION_DATE_FORMAT.format(Instant.now());

        String message;
        if (summary.operation() == BulkCaseOperationType.INJECTION) {
            // The ZM injected their own field work — credit them as the responsible actor.
            message = count + " " + caseLabel + " " + plural + " injected by ZM "
                    + safe(summary.zmName()) + " on " + operationDate + " — ready to work.";
        } else {
            // Admin distributed imported cases that were routed to this ZM.
            message = count + " " + caseLabel + " " + plural + " assigned to you by Admin on "
                    + operationDate + " — ready to work.";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        data.put("caseType", summary.caseType() != null ? summary.caseType().name() : null);
        data.put("module", summary.module() != null ? summary.module().name() : null);
        data.put("operation", summary.operation() != null ? summary.operation().name() : null);
        data.put("batchId", summary.batchId());
        data.put("operationDate", operationDate);
        data.put("zmName", summary.zmName());
        data.put("routeHint", summary.routeHint());

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("CASES_BULK_ASSIGNED_TO_ZM");
        dto.setTitle(count + " " + caseLabel + " " + plural + " ready to work");
        dto.setMessage(message);
        dto.setEntityType("CASE_BATCH");
        dto.setEntityId(summary.batchId());
        dto.setStatus("ASSIGNED_TO_ZM");
        dto.setTriggeredBy(summary.triggeredBy());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("INFO");
        dto.setCategory("CASE");
        dto.setActionType("OPEN_CASE");
        return dto;
    }

    private String bulkCaseTypeLabel(CaseType caseType, CaseModuleType module) {
        if (caseType == CaseType.ACTIVATION) {
            return "activation";
        }
        if (module == CaseModuleType.RES || caseType == CaseType.RESILIATION) {
            return "resiliation";
        }
        return "complaint";
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

        dto.setLevel("WARNING");
        dto.setCategory("SLA");
        dto.setActionType("OPEN_CASE");
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

        dto.setLevel("CRITICAL");
        dto.setCategory("SLA");
        dto.setActionType("OPEN_CASE");
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

        dto.setLevel("CRITICAL");
        dto.setCategory("SLA");
        dto.setActionType("OPEN_CASE");
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
        dto.setLevel("INFO");
        dto.setCategory("DPM");
        dto.setActionType("OPEN_DPM");
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
        dto.setLevel("INFO");
        dto.setCategory("DPM");
        dto.setActionType("OPEN_DPM");
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
        dto.setLevel("WARNING");
        dto.setCategory("DPM");
        dto.setActionType("OPEN_DPM");
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
        dto.setLevel("INFO");
        dto.setCategory("DPM");
        dto.setActionType("OPEN_DPM");
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
        dto.setLevel("INFO");
        dto.setCategory("DPM");
        dto.setActionType("OPEN_DPM");
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
        dto.setLevel("WARNING");
        dto.setCategory("DPM");
        dto.setActionType("OPEN_DPM");
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
        dto.setLevel("INFO");
        dto.setCategory("STOCK");
        dto.setActionType("OPEN_STOCK");
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

    // ── Material Return payloads ───────────────────────────────────────────────

    public NotificationMessageDto buildMaterialReturnSubmitted(MaterialReturnRequest req) {
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("sttId", req.getSttId());
        data.put("sttName", req.getSttName());
        data.put("reason", req.getReason() != null ? req.getReason().name() : null);
        data.put("destination", req.getDestination() != null ? req.getDestination().name() : null);
        data.put("itemCount", req.getItems() != null ? req.getItems().size() : 0);

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("MATERIAL_RETURN_SUBMITTED");
        dto.setTitle("Material Return Pending Review");
        dto.setMessage("STT " + safe(req.getSttName()) + " submitted return "
                + safe(req.getReturnCode()) + " for your validation.");
        dto.setEntityType("MATERIAL_RETURN");
        dto.setEntityId(req.getId());
        dto.setStatus("PENDING");
        dto.setTriggeredBy(req.getSttEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("WARNING");
        dto.setCategory("MATERIAL_RETURN");
        dto.setActionType("OPEN_MATERIAL_RETURN");
        dto.setRelatedStockRequestId(req.getId());
        if (req.getRelatedCaseId() != null) dto.setRelatedCaseId(req.getRelatedCaseId());
        return dto;
    }

    public NotificationMessageDto buildMaterialReturnRejected(MaterialReturnRequest req) {
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("zmName", req.getReviewedByZmName());
        data.put("reviewComment", req.getReviewComment());

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("MATERIAL_RETURN_REJECTED");
        dto.setTitle("Material Return Rejected");
        dto.setMessage("Your return " + safe(req.getReturnCode())
                + " was rejected by ZM " + safe(req.getReviewedByZmName()) + "."
                + (req.getReviewComment() != null ? " Reason: " + req.getReviewComment() : ""));
        dto.setEntityType("MATERIAL_RETURN");
        dto.setEntityId(req.getId());
        dto.setStatus("REJECTED");
        dto.setTriggeredBy(req.getZmEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("WARNING");
        dto.setCategory("MATERIAL_RETURN");
        dto.setActionType("OPEN_MATERIAL_RETURN");
        dto.setRelatedStockRequestId(req.getId());
        if (req.getRelatedCaseId() != null) dto.setRelatedCaseId(req.getRelatedCaseId());
        return dto;
    }

    public NotificationMessageDto buildHandoverScheduleProposed(MaterialReturnRequest req) {
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("zmName", req.getZmName());
        data.put("handoverDate", req.getProposedHandoverDate());
        data.put("handoverTime", req.getProposedHandoverTime());
        data.put("handoverNote", req.getProposedHandoverNote());
        data.put("routeHint", "/stt/stock/returns");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("MATERIAL_RETURN_SCHEDULE_PROPOSED");
        dto.setTitle("Handover schedule proposed — action required");
        dto.setMessage("ZM " + safe(req.getZmName()) + " proposed a handover for return "
                + safe(req.getReturnCode()) + " on " + safe(req.getProposedHandoverDate())
                + " at " + safe(req.getProposedHandoverTime()) + ". Please confirm or request a new slot.");
        dto.setEntityType("MATERIAL_RETURN");
        dto.setEntityId(req.getId());
        dto.setStatus("SCHEDULE_PROPOSED");
        dto.setTriggeredBy(req.getZmEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("WARNING");
        dto.setCategory("MATERIAL_RETURN");
        dto.setActionType("OPEN_MATERIAL_RETURN");
        dto.setRelatedStockRequestId(req.getId());
        if (req.getRelatedCaseId() != null) dto.setRelatedCaseId(req.getRelatedCaseId());
        return dto;
    }

    public NotificationMessageDto buildScheduleAcceptedByStt(MaterialReturnRequest req) {
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("sttName", req.getSttName());
        data.put("handoverDate", req.getProposedHandoverDate());
        data.put("handoverTime", req.getProposedHandoverTime());
        data.put("routeHint", "/zm/stock/returns");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("MATERIAL_RETURN_SCHEDULE_CONFIRMED");
        dto.setTitle("Handover schedule confirmed by STT");
        dto.setMessage("STT " + safe(req.getSttName()) + " confirmed the handover for return "
                + safe(req.getReturnCode()) + " on " + safe(req.getProposedHandoverDate())
                + " at " + safe(req.getProposedHandoverTime()) + ".");
        dto.setEntityType("MATERIAL_RETURN");
        dto.setEntityId(req.getId());
        dto.setStatus("SCHEDULE_CONFIRMED");
        dto.setTriggeredBy(req.getSttEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("INFO");
        dto.setCategory("MATERIAL_RETURN");
        dto.setActionType("OPEN_MATERIAL_RETURN");
        dto.setRelatedStockRequestId(req.getId());
        if (req.getRelatedCaseId() != null) dto.setRelatedCaseId(req.getRelatedCaseId());
        return dto;
    }

    public NotificationMessageDto buildMaterialHandedOver(MaterialReturnRequest req) {
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("zmName", req.getZmName());
        data.put("routeHint", "/stt/stock/returns");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("MATERIAL_RETURN_HANDED_OVER");
        dto.setTitle("Material return completed");
        dto.setMessage("ZM " + safe(req.getZmName()) + " confirmed receipt of return "
                + safe(req.getReturnCode()) + ". The handover is now complete.");
        dto.setEntityType("MATERIAL_RETURN");
        dto.setEntityId(req.getId());
        dto.setStatus("HANDED_OVER");
        dto.setTriggeredBy(req.getZmEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("INFO");
        dto.setCategory("MATERIAL_RETURN");
        dto.setActionType("OPEN_MATERIAL_RETURN");
        dto.setRelatedStockRequestId(req.getId());
        if (req.getRelatedCaseId() != null) dto.setRelatedCaseId(req.getRelatedCaseId());
        return dto;
    }

    public NotificationMessageDto buildScheduleRejectedByStt(MaterialReturnRequest req) {
        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("sttName", req.getSttName());
        data.put("rescheduleComment", req.getSttScheduleResponseComment());
        data.put("routeHint", "/zm/stock/returns");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("MATERIAL_RETURN_RESCHEDULE_REQUESTED");
        dto.setTitle("STT requested a new handover slot");
        dto.setMessage("STT " + safe(req.getSttName()) + " declined the proposed schedule for return "
                + safe(req.getReturnCode()) + " and is requesting a new date/time."
                + (req.getSttScheduleResponseComment() != null
                        ? " Reason: " + req.getSttScheduleResponseComment() : ""));
        dto.setEntityType("MATERIAL_RETURN");
        dto.setEntityId(req.getId());
        dto.setStatus("RESCHEDULE_REQUESTED");
        dto.setTriggeredBy(req.getSttEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("WARNING");
        dto.setCategory("MATERIAL_RETURN");
        dto.setActionType("OPEN_MATERIAL_RETURN");
        dto.setRelatedStockRequestId(req.getId());
        if (req.getRelatedCaseId() != null) dto.setRelatedCaseId(req.getRelatedCaseId());
        return dto;
    }

    /** Sent to ZM after they confirm physical handover — closes the return loop for their Alert Center. */
    public NotificationMessageDto buildReturnClosedForZm(MaterialReturnRequest req) {
        String itemSummary = buildItemSummary(req.getItems());

        Map<String, Object> data = new HashMap<>();
        data.put("returnId", req.getId());
        data.put("returnCode", req.getReturnCode());
        data.put("sttName", req.getSttName());
        data.put("sttId", req.getSttId());
        data.put("itemCount", req.getItems() != null ? req.getItems().size() : 0);
        data.put("destination", req.getDestination() != null ? req.getDestination().name() : null);
        data.put("itemSummary", itemSummary);
        data.put("routeHint", "/zm/stock/returns");

        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setEventType("MATERIAL_RETURN_CLOSED");
        dto.setTitle("Return closed — STT stock updated");
        dto.setMessage("Return " + safe(req.getReturnCode()) + " from STT " + safe(req.getSttName())
                + " is now closed. Returned items: " + itemSummary + ".");
        dto.setEntityType("MATERIAL_RETURN");
        dto.setEntityId(req.getId());
        dto.setStatus("HANDED_OVER");
        dto.setTriggeredBy(req.getZmEmail());
        dto.setCreatedAt(Instant.now());
        dto.setData(data);
        dto.setLevel("INFO");
        dto.setCategory("MATERIAL_RETURN");
        dto.setActionType("OPEN_MATERIAL_RETURN");
        dto.setRelatedStockRequestId(req.getId());
        if (req.getRelatedCaseId() != null) dto.setRelatedCaseId(req.getRelatedCaseId());
        return dto;
    }

    private String buildItemSummary(List<com.pfe.stock_management.material_return.ReturnedItem> items) {
        if (items == null || items.isEmpty()) return "no items";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            var item = items.get(i);
            sb.append(item.getQuantity()).append("× ").append(safe(item.getEquipmentName()));
            if (item.getCondition() != null) sb.append(" (").append(item.getCondition().name()).append(")");
        }
        return sb.toString();
    }
}
