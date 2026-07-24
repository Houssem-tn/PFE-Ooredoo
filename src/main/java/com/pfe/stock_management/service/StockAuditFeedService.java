package com.pfe.stock_management.service;

import com.pfe.stock_management.dto.StockAuditEntryDto;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.material_return.MaterialReturnRepository;
import com.pfe.stock_management.material_return.MaterialReturnRequest;
import com.pfe.stock_management.material_return.MaterialReturnStatus;
import com.pfe.stock_management.material_return.ReturnTimelineEvent;
import com.pfe.stock_management.material_return.ReturnedItem;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.model.DpmTimelineEvent;
import com.pfe.stock_management.repository.DpmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-only aggregation that surfaces both DPM distribution requests and material
 * return requests in a single, newest-first audit feed for the Admin Stock Audits
 * page. It maps each source entity into a common {@link StockAuditEntryDto}; it
 * never mutates either workflow.
 */
@Service
@RequiredArgsConstructor
public class StockAuditFeedService {

    public static final String TYPE_DPM = "DPM";
    public static final String TYPE_RETURN = "RETURN_MATERIAL";

    private final DpmRepository dpmRepository;
    private final MaterialReturnRepository returnRepository;

    /**
     * Builds the merged audit feed, sorted by last activity (newest first).
     *
     * @param auditType optional filter — {@code DPM}, {@code RETURN_MATERIAL}/{@code RETURN},
     *                  or {@code ALL}/null for both.
     */
    public List<StockAuditEntryDto> getEntries(String auditType) {
        boolean wantDpm = wantsDpm(auditType);
        boolean wantReturns = wantsReturns(auditType);

        List<StockAuditEntryDto> entries = new ArrayList<>();
        if (wantDpm) {
            dpmRepository.findAll().forEach(d -> entries.add(toEntry(d)));
        }
        if (wantReturns) {
            returnRepository.findAll().forEach(r -> entries.add(toEntry(r)));
        }

        entries.sort(Comparator.comparing(StockAuditFeedService::sortKey).reversed());
        return entries;
    }

    private static Instant sortKey(StockAuditEntryDto e) {
        if (e.getUpdatedAt() != null) return e.getUpdatedAt();
        if (e.getCreatedAt() != null) return e.getCreatedAt();
        return Instant.EPOCH;
    }

    private boolean wantsDpm(String auditType) {
        if (auditType == null || auditType.isBlank()) return true;
        String t = auditType.trim().toUpperCase(Locale.ROOT);
        return t.equals("ALL") || t.equals("DPM");
    }

    private boolean wantsReturns(String auditType) {
        if (auditType == null || auditType.isBlank()) return true;
        String t = auditType.trim().toUpperCase(Locale.ROOT);
        return t.equals("ALL") || t.equals("RETURN_MATERIAL") || t.equals("RETURN") || t.equals("RETURNS");
    }

    // ── DPM mapping ─────────────────────────────────────────────────────────

    private StockAuditEntryDto toEntry(DpmRequest d) {
        List<StockAuditEntryDto.Item> items = new ArrayList<>();
        int totalQty = 0;
        if (d.getItems() != null) {
            for (DpmItem it : d.getItems()) {
                items.add(StockAuditEntryDto.Item.builder()
                        .equipmentId(it.getEquipmentId())
                        .equipmentName(it.getEquipmentName())
                        .unit(it.getUnit())
                        .quantity(it.getRequestedQuantity())
                        .build());
                totalQty += it.getRequestedQuantity();
            }
        }

        List<StockAuditEntryDto.Event> timeline = new ArrayList<>();
        DpmTimelineEvent lastEvent = null;
        if (d.getTimeline() != null) {
            for (DpmTimelineEvent ev : d.getTimeline()) {
                timeline.add(StockAuditEntryDto.Event.builder()
                        .eventType(ev.getEventType())
                        .description(ev.getDescription())
                        .actorName(ev.getPerformedBy())
                        .actorRole(displayRole(ev.getPerformedByRole()))
                        .timestamp(ev.getOccurredAt())
                        .build());
                if (ev.getOccurredAt() != null
                        && (lastEvent == null || lastEvent.getOccurredAt() == null
                            || ev.getOccurredAt().isAfter(lastEvent.getOccurredAt()))) {
                    lastEvent = ev;
                }
            }
        }

        // Primary actor = whoever last acted; fall back to the requesting ZM.
        String actorRole = "ZM";
        String actorName = d.getZmName();
        if (lastEvent != null) {
            String role = displayRole(lastEvent.getPerformedByRole());
            if (role != null && !role.isBlank()) actorRole = role;
            actorName = resolveDpmActorName(role, d, lastEvent.getPerformedBy());
        }

        return StockAuditEntryDto.builder()
                .auditType(TYPE_DPM)
                .id(d.getId())
                .reference(d.getDpmNumber())
                .status(d.getStatus() != null ? d.getStatus().name() : null)
                .statusLabel(dpmStatusLabel(d.getStatus()))
                .statusCategory(dpmStatusCategory(d.getStatus()))
                .actorName(actorName)
                .actorRole(actorRole)
                .sttId(d.getSttId())
                .sttName(d.getSttName())
                .zmId(d.getZmId())
                .zmName(d.getZmName())
                .items(items)
                .totalQuantity(totalQty)
                .notes(d.getNotes())
                .createdAt(d.getCreatedAt())
                .updatedAt(lastActivity(d, lastEvent))
                .timeline(timeline)
                .build();
    }

    private String resolveDpmActorName(String role, DpmRequest d, String fallback) {
        if (role != null) {
            if (role.equalsIgnoreCase("ZM") && d.getZmName() != null) return d.getZmName();
            if (role.equalsIgnoreCase("STT") && d.getSttName() != null) return d.getSttName();
        }
        return (fallback != null && !fallback.isBlank()) ? fallback : d.getZmName();
    }

    private Instant lastActivity(DpmRequest d, DpmTimelineEvent lastEvent) {
        Instant latest = d.getCreatedAt();
        latest = max(latest, d.getConfirmedAt());
        latest = max(latest, d.getRdvScheduledAt());
        latest = max(latest, d.getExternalApprovalMarkedAt());
        latest = max(latest, d.getExternalRejectedAt());
        if (lastEvent != null) latest = max(latest, lastEvent.getOccurredAt());
        return latest;
    }

    // ── Material return mapping ─────────────────────────────────────────────

    private StockAuditEntryDto toEntry(MaterialReturnRequest r) {
        List<StockAuditEntryDto.Item> items = new ArrayList<>();
        int totalQty = 0;
        if (r.getItems() != null) {
            for (ReturnedItem it : r.getItems()) {
                items.add(StockAuditEntryDto.Item.builder()
                        .equipmentId(it.getEquipmentId())
                        .equipmentName(it.getEquipmentName())
                        .unit(it.getUnit())
                        .quantity(it.getQuantity())
                        .condition(it.getCondition() != null ? it.getCondition().name() : null)
                        .serialNumber(it.getSerialNumber())
                        .imei(it.getImei())
                        .retrievedStockItemId(it.getRetrievedStockItemId())
                        .technicianName(it.getOriginalTechnicianName())
                        .originalCaseReference(it.getOriginalCaseReference())
                        .originalCaseType(it.getOriginalCaseType())
                        .originalCustomerName(it.getOriginalCustomerName())
                        .originalRecoveryDate(it.getOriginalRecoveryDate())
                        .sttCollectedAt(it.getSttCollectedAt())
                        .currentReturnStatus(r.getStatus() != null ? r.getStatus().name() : null)
                        .proofViewUrl(it.getProofViewUrl())
                        .build());
                totalQty += it.getQuantity();
            }
        }

        List<StockAuditEntryDto.Event> timeline = new ArrayList<>();
        ReturnTimelineEvent lastEvent = null;
        if (r.getTimeline() != null) {
            for (ReturnTimelineEvent ev : r.getTimeline()) {
                timeline.add(StockAuditEntryDto.Event.builder()
                        .eventType(ev.getEventType())
                        .description(ev.getDescription())
                        .actorName(ev.getActorName() != null ? ev.getActorName() : ev.getActor())
                        .actorRole(returnEventRole(ev.getEventType()))
                        .timestamp(ev.getTimestamp())
                        .note(ev.getNote())
                        .build());
                if (ev.getTimestamp() != null
                        && (lastEvent == null || lastEvent.getTimestamp() == null
                            || ev.getTimestamp().isAfter(lastEvent.getTimestamp()))) {
                    lastEvent = ev;
                }
            }
        }

        // Primary actor = whoever last acted; fall back to the submitting STT.
        String actorRole = "STT";
        String actorName = r.getSttName();
        if (lastEvent != null) {
            actorRole = returnEventRole(lastEvent.getEventType());
            actorName = lastEvent.getActorName() != null ? lastEvent.getActorName()
                    : ("ZM".equals(actorRole) ? r.getZmName() : r.getSttName());
        }

        return StockAuditEntryDto.builder()
                .auditType(TYPE_RETURN)
                .id(r.getId())
                .reference(r.getReturnCode())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .statusLabel(returnStatusLabel(r.getStatus()))
                .statusCategory(returnStatusCategory(r.getStatus()))
                .actorName(actorName)
                .actorRole(actorRole)
                .sttId(r.getSttId())
                .sttName(r.getSttName())
                .zmId(r.getZmId())
                .zmName(r.getZmName())
                .items(items)
                .totalQuantity(totalQty)
                .reason(r.getReason() != null ? r.getReason().name() : null)
                .returnSource(r.getReturnSource() != null ? r.getReturnSource().name() : null)
                .returnCondition(r.getCondition() != null ? r.getCondition().name() : null)
                .destination(r.getDestination() != null ? r.getDestination().name() : null)
                .relatedCaseId(r.getRelatedCaseId())
                .crmCase(r.getCrmCase())
                .relatedCaseType(r.getRelatedCaseType())
                .notes(r.getComment())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt() : r.getSubmittedAt())
                .updatedAt(returnLastActivity(r, lastEvent))
                .timeline(timeline)
                .build();
    }

    private Instant returnLastActivity(MaterialReturnRequest r, ReturnTimelineEvent lastEvent) {
        Instant latest = r.getCreatedAt() != null ? r.getCreatedAt() : r.getSubmittedAt();
        latest = max(latest, r.getSubmittedAt());
        latest = max(latest, r.getScheduleProposedAt());
        latest = max(latest, r.getScheduleConfirmedAt());
        latest = max(latest, r.getScheduleRejectedAt());
        latest = max(latest, r.getReviewedAt());
        latest = max(latest, r.getHandedOverAt());
        if (lastEvent != null) latest = max(latest, lastEvent.getTimestamp());
        return latest;
    }

    /** Best-effort role for a return lifecycle event (returns flow STT -> ZM). */
    private String returnEventRole(String eventType) {
        if (eventType == null) return "STT";
        return switch (eventType) {
            case "SCHEDULE_PROPOSED", "REJECTED", "HANDED_OVER" -> "ZM";
            default -> "STT"; // SUBMITTED, SCHEDULE_CONFIRMED, RESCHEDULE_REQUESTED, ...
        };
    }

    // ── Status helpers ──────────────────────────────────────────────────────

    private String displayRole(String rawRole) {
        if (rawRole == null) return null;
        String r = rawRole.trim().toUpperCase(Locale.ROOT);
        if (r.startsWith("ROLE_")) r = r.substring(5);
        return r;
    }

    private Instant max(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private String dpmStatusCategory(DpmStatus s) {
        if (s == null) return "PENDING";
        return switch (s) {
            case WAITING_TEAM_STOCK_APPROVAL, PENDING_STT_CONFIRMATION -> "PENDING";
            case RDV_SCHEDULED, RDV_CONFIRMED_BY_STT, MATERIAL_RECEIVED, PARTIALLY_CONFIRMED -> "IN_PROGRESS";
            case FULLY_CONFIRMED -> "CONFIRMED";
            case COMPLETED -> "COMPLETED";
            case TEAM_STOCK_REJECTED, REJECTED -> "REJECTED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private String dpmStatusLabel(DpmStatus s) {
        if (s == null) return "—";
        return switch (s) {
            case WAITING_TEAM_STOCK_APPROVAL -> "Waiting Approval";
            case TEAM_STOCK_REJECTED -> "Ext. Rejected";
            case RDV_SCHEDULED -> "RDV Scheduled";
            case RDV_CONFIRMED_BY_STT -> "RDV Confirmed";
            case MATERIAL_RECEIVED -> "Material Received";
            case COMPLETED -> "Completed";
            case PENDING_STT_CONFIRMATION -> "Pending";
            case PARTIALLY_CONFIRMED -> "Partial";
            case FULLY_CONFIRMED -> "Confirmed";
            case REJECTED -> "Rejected";
            case CANCELLED -> "Cancelled";
        };
    }

    private String returnStatusCategory(MaterialReturnStatus s) {
        if (s == null) return "PENDING";
        return switch (s) {
            case PENDING, SUBMITTED, PENDING_ZM_REVIEW -> "PENDING";
            case SCHEDULE_PROPOSED, PENDING_STT_CONFIRMATION, WAITING_STT_CONFIRMATION,
                 SCHEDULE_PROPOSED_BY_ZM, RESCHEDULE_REQUESTED, SCHEDULE_REJECTED_BY_STT -> "IN_PROGRESS";
            case SCHEDULE_CONFIRMED, APPROVED -> "CONFIRMED";
            case HANDED_OVER, RECEIVED, COMPLETED, CLOSED -> "COMPLETED";
            case REJECTED -> "REJECTED";
        };
    }

    private String returnStatusLabel(MaterialReturnStatus s) {
        if (s == null) return "—";
        return switch (s) {
            case PENDING, SUBMITTED, PENDING_ZM_REVIEW -> "Pending ZM Review";
            case SCHEDULE_PROPOSED, SCHEDULE_PROPOSED_BY_ZM -> "Schedule Proposed";
            case PENDING_STT_CONFIRMATION, WAITING_STT_CONFIRMATION -> "Awaiting STT";
            case RESCHEDULE_REQUESTED, SCHEDULE_REJECTED_BY_STT -> "Reschedule Requested";
            case SCHEDULE_CONFIRMED, APPROVED -> "Schedule Confirmed";
            case HANDED_OVER, RECEIVED -> "Handed Over";
            case COMPLETED -> "Completed";
            case CLOSED -> "Closed";
            case REJECTED -> "Rejected";
        };
    }
}
