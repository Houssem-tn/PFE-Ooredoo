package com.pfe.activation_analytics.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsDistributionSummaryResponse;
import com.pfe.activation_analytics.model.ActivationImportSharedResult;
import com.pfe.activation_analytics.model.ActivationImportSharedRow;
import com.pfe.case_management.dto.BulkCaseAssignmentSummary;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.model.BulkCaseOperationType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationDistributionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ActivationDistributionProcessingService.class);

    private final CaseRepository caseRepository;
    private final CaseTimelineService caseTimelineService;
    private final NotificationEventService notificationEventService;
    private final ActivationDistributionEligibilityService eligibilityService;
    private final ActivationDistributionRoutingService routingService;

    public AdminActivationAnalyticsDistributionSummaryResponse process(
            ActivationImportSharedResult sharedResult,
            String importedBy
    ) {
        int createdCases = 0;
        int updatedExistingCases = 0;
        int assignedToZmCases = 0;
        int sentToAdminQueueCases = 0;
        int sourceCompletedRows = 0;
        int sourceTerminatedRows = 0;
        int noGovernorateRows = 0;

        // One distribution run = one batch. We aggregate newly-created cases per target ZM so each
        // ZM receives a single summary alert instead of one notification per routed case.
        String batchId = UUID.randomUUID().toString();
        Map<String, ZmBatchAccumulator> assignmentsByZmEmail = new LinkedHashMap<>();

        for (ActivationImportSharedRow row : sharedResult.rowsForDistribution()) {
            if (eligibilityService.isActionable(row)) {
                DistributionOutcome outcome = upsertActionableCase(row, importedBy);
                if (outcome.created()) {
                    createdCases += 1;
                } else {
                    updatedExistingCases += 1;
                }
                if (outcome.assignedToZm()) {
                    assignedToZmCases += 1;
                } else {
                    sentToAdminQueueCases += 1;
                }
                if (outcome.noGovernorate()) {
                    noGovernorateRows += 1;
                }
                if (outcome.created() && outcome.assignedToZm() && ActivationAnalyticsSupport.hasText(outcome.zmEmail())) {
                    assignmentsByZmEmail
                            .computeIfAbsent(outcome.zmEmail(), email -> new ZmBatchAccumulator(outcome.zmName()))
                            .increment();
                }
                continue;
            }

            if (eligibilityService.isSourceCompleted(row)) {
                sourceCompletedRows += 1;
                syncExistingCaseSourceOnly(row, importedBy, "Source signalled completed work while the case is not distributed as active work.");
                continue;
            }

            if (eligibilityService.isSourceTerminated(row)) {
                sourceTerminatedRows += 1;
                syncExistingCaseSourceOnly(row, importedBy, "Source signalled a terminated activation row. Workflow assignment was left unchanged.");
            }
        }

        emitAggregatedAssignmentNotifications(assignmentsByZmEmail, batchId, importedBy);

        return new AdminActivationAnalyticsDistributionSummaryResponse(
                sharedResult.processableRows(),
                sharedResult.actionableRows(),
                createdCases,
                updatedExistingCases,
                assignedToZmCases,
                sentToAdminQueueCases,
                sourceCompletedRows,
                sourceTerminatedRows,
                noGovernorateRows
        );
    }

    private DistributionOutcome upsertActionableCase(ActivationImportSharedRow row, String importedBy) {
        Optional<CaseEntity> existingCase = caseRepository.findByTypeAndCrmCase(CaseType.ACTIVATION, row.crmCase());
        if (existingCase.isPresent()) {
            CaseEntity caseEntity = existingCase.get();
            syncSourceSnapshot(caseEntity, row, importedBy);
            caseTimelineService.append(
                    caseEntity,
                    CaseTimelineEventType.STATUS_CHANGED,
                    "Activation source refreshed",
                    "The imported activation source refreshed the case details without changing the current workflow assignee.",
                    importedBy,
                    caseEntity.getStatus(),
                    caseEntity.getStatus(),
                    sourceSyncTimelineData(row)
            );
            caseEntity.setUpdatedAt(Instant.now());
            caseRepository.save(caseEntity);
            // Existing-case refresh: no new assignment, so no aggregated notification is counted.
            return new DistributionOutcome(false, ActivationAnalyticsSupport.hasText(caseEntity.getZmId()), false, null, null);
        }

        Instant now = Instant.now();
        ActivationDistributionRoutingService.RoutingDecision routingDecision =
                routingService.resolveZmForGovernorate(row.gouvernorat());
        AppUser assignedZm = routingDecision.zm();

        CaseEntity caseEntity = CaseEntity.builder()
                .type(CaseType.ACTIVATION)
                .crmCase(row.crmCase())
                .dateCreationCrm(ActivationAnalyticsSupport.toInstant(row.dateCreationCrmDate()))
                .openingDateTimos(ActivationAnalyticsSupport.toInstant(row.openingDateSurTimosDate()))
                .status(assignedZm != null ? CaseStatus.ASSIGNED_TO_ZM : CaseStatus.CREATED)
                .clientName(row.client())
                .clientPhone(row.contactClient())
                .msisdn(row.msisdn())
                .commentaire(row.commentaire())
                .latitude(row.latitudeSite())
                .longitude(row.longitudeSite())
                .localite(row.localite())
                .delegation(row.delegation())
                .gouvernorat(row.gouvernorat())
                .siteSecteur(row.siteSecteur())
                .offre(row.offre())
                .desPack(row.desPack())
                .ownerZmId(assignedZm != null ? assignedZm.getId() : null)
                .ownerZmName(assignedZm != null ? assignedZm.getFullName() : null)
                .zmId(assignedZm != null ? assignedZm.getId() : null)
                .zmEmail(assignedZm != null ? assignedZm.getEmail() : null)
                .zmName(assignedZm != null ? assignedZm.getFullName() : null)
                .dateAffectationZm(assignedZm != null ? now : null)
                .sourceAssignedSttName(row.nameStt())
                .sourceStatut(row.statut())
                .sourceRepTravauxStt(row.repTravauxStt())
                .sourceGovernorate(row.gouvernorat())
                .sourceLastImportedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(importedBy)
                .build();

        caseTimelineService.append(
                caseEntity,
                CaseTimelineEventType.CASE_CREATED,
                "Activation case created from imported source",
                "A new operational activation case was created from the shared import pipeline.",
                importedBy,
                null,
                caseEntity.getStatus(),
                sourceSyncTimelineData(row)
        );

        caseTimelineService.append(
                caseEntity,
                CaseTimelineEventType.ASSIGNMENT_UPDATED,
                assignedZm != null ? "ZM routing resolved" : "Sent to admin queue",
                assignedZm != null
                        ? "Governorate " + row.gouvernorat() + " was routed to ZM "
                        + safe(assignedZm.getFullName(), assignedZm.getEmail())
                        + " with the lowest active workload."
                        : "No active ZM currently covers governorate " + safe(row.gouvernorat(), "-")
                        + ". The case remains in the admin queue.",
                importedBy,
                caseEntity.getStatus(),
                caseEntity.getStatus(),
                new LinkedHashMap<>(sourceSyncTimelineData(row))
        );

        // The per-case assignment notification is intentionally NOT emitted here. Newly created
        // cases are accumulated by the caller and surface as ONE aggregated summary per ZM at the
        // end of the distribution run (see emitAggregatedAssignmentNotifications).
        caseRepository.save(caseEntity);
        return new DistributionOutcome(
                true,
                assignedZm != null,
                !ActivationAnalyticsSupport.hasText(row.gouvernorat()),
                assignedZm != null ? assignedZm.getEmail() : null,
                assignedZm != null ? assignedZm.getFullName() : null
        );
    }

    /**
     * Sends one aggregated "N activation cases assigned to you by Admin … — ready to work" alert per
     * target ZM, replacing the previous one-notification-per-case behaviour. Each ZM's notification
     * is independent, so a failure for one ZM never blocks the others.
     */
    private void emitAggregatedAssignmentNotifications(
            Map<String, ZmBatchAccumulator> assignmentsByZmEmail,
            String batchId,
            String importedBy
    ) {
        for (Map.Entry<String, ZmBatchAccumulator> entry : assignmentsByZmEmail.entrySet()) {
            String zmEmail = entry.getKey();
            ZmBatchAccumulator accumulator = entry.getValue();
            try {
                notificationEventService.onCasesBulkAssignedToZm(new BulkCaseAssignmentSummary(
                        zmEmail,
                        accumulator.zmName(),
                        CaseType.ACTIVATION,
                        null,
                        BulkCaseOperationType.DISTRIBUTION,
                        accumulator.count(),
                        batchId,
                        importedBy,
                        "/zm/cases/activation"
                ));
            } catch (Exception notifEx) {
                log.warn("activation_distribution: aggregated notification failed batchId={} zmEmail={}: {}",
                        batchId, zmEmail, notifEx.getMessage());
            }
        }
    }

    private void syncExistingCaseSourceOnly(ActivationImportSharedRow row, String importedBy, String message) {
        caseRepository.findByTypeAndCrmCase(CaseType.ACTIVATION, row.crmCase()).ifPresent(caseEntity -> {
            syncSourceSnapshot(caseEntity, row, importedBy);
            caseTimelineService.append(
                    caseEntity,
                    CaseTimelineEventType.STATUS_CHANGED,
                    "Activation source status refreshed",
                    message,
                    importedBy,
                    caseEntity.getStatus(),
                    caseEntity.getStatus(),
                    sourceSyncTimelineData(row)
            );
            caseEntity.setUpdatedAt(Instant.now());
            caseRepository.save(caseEntity);
        });
    }

    private void syncSourceSnapshot(CaseEntity caseEntity, ActivationImportSharedRow row, String importedBy) {
        Instant now = Instant.now();
        caseEntity.setSourceAssignedSttName(row.nameStt());
        caseEntity.setSourceStatut(row.statut());
        caseEntity.setSourceRepTravauxStt(row.repTravauxStt());
        caseEntity.setSourceGovernorate(row.gouvernorat());
        caseEntity.setSourceLastImportedAt(now);
        caseEntity.setUpdatedAt(now);

        if (!ActivationAnalyticsSupport.hasText(caseEntity.getClientName()) && ActivationAnalyticsSupport.hasText(row.client())) {
            caseEntity.setClientName(row.client());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getClientPhone()) && ActivationAnalyticsSupport.hasText(row.contactClient())) {
            caseEntity.setClientPhone(row.contactClient());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getMsisdn()) && ActivationAnalyticsSupport.hasText(row.msisdn())) {
            caseEntity.setMsisdn(row.msisdn());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getCommentaire()) && ActivationAnalyticsSupport.hasText(row.commentaire())) {
            caseEntity.setCommentaire(row.commentaire());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getLatitude()) && ActivationAnalyticsSupport.hasText(row.latitudeSite())) {
            caseEntity.setLatitude(row.latitudeSite());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getLongitude()) && ActivationAnalyticsSupport.hasText(row.longitudeSite())) {
            caseEntity.setLongitude(row.longitudeSite());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getLocalite()) && ActivationAnalyticsSupport.hasText(row.localite())) {
            caseEntity.setLocalite(row.localite());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getDelegation()) && ActivationAnalyticsSupport.hasText(row.delegation())) {
            caseEntity.setDelegation(row.delegation());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getOffre()) && ActivationAnalyticsSupport.hasText(row.offre())) {
            caseEntity.setOffre(row.offre());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getDesPack()) && ActivationAnalyticsSupport.hasText(row.desPack())) {
            caseEntity.setDesPack(row.desPack());
        }
        if (!ActivationAnalyticsSupport.hasText(caseEntity.getSiteSecteur()) && ActivationAnalyticsSupport.hasText(row.siteSecteur())) {
            caseEntity.setSiteSecteur(row.siteSecteur());
        }
    }

    private LinkedHashMap<String, Object> sourceSyncTimelineData(ActivationImportSharedRow row) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("crmCase", row.crmCase());
        data.put("sourceGovernorate", row.gouvernorat());
        data.put("sourceAssignedSttName", row.nameStt());
        data.put("sourceStatut", row.statut());
        data.put("sourceRepTravauxStt", row.repTravauxStt());
        return data;
    }

    private String safe(String primary, String fallback) {
        if (ActivationAnalyticsSupport.hasText(primary)) {
            return primary.trim();
        }
        if (ActivationAnalyticsSupport.hasText(fallback)) {
            return fallback.trim();
        }
        return "-";
    }

    private record DistributionOutcome(
            boolean created,
            boolean assignedToZm,
            boolean noGovernorate,
            String zmEmail,
            String zmName
    ) {
    }

    /** Mutable per-ZM counter used to aggregate a distribution batch into a single notification. */
    private static final class ZmBatchAccumulator {
        private final String zmName;
        private int count;

        private ZmBatchAccumulator(String zmName) {
            this.zmName = zmName;
        }

        private void increment() {
            this.count += 1;
        }

        private String zmName() {
            return zmName;
        }

        private int count() {
            return count;
        }
    }
}
