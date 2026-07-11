package com.pfe.plainte_analytics.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ResolutionValidationStatus;
import com.pfe.case_management.model.ComplaintResolutionInfo;
import com.pfe.case_management.model.RdvInfo;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.model.ZmValidation;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseService;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsDistributionSummaryResponse;
import com.pfe.plainte_analytics.model.PlainteImportSharedResult;
import com.pfe.plainte_analytics.model.PlainteImportSharedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlainteDistributionProcessingService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseService caseService;
    private final CaseTimelineService caseTimelineService;
    private final NotificationEventService notificationEventService;
    private final PlainteDistributionEligibilityService eligibilityService;
    private final PlainteDistributionRoutingService routingService;

    public AdminPlainteAnalyticsDistributionSummaryResponse process(
            PlainteImportSharedResult sharedResult,
            String importedBy
    ) {
        List<PlainteImportSharedRow> rows = sharedResult.rowsForDistribution();
        ImportResolutionContext context = prepareContext(rows);

        int createdCases = 0;
        int updatedExistingCases = 0;
        int assignedToZmCases = 0;
        int sentToAdminQueueCases = 0;
        int sourceCompletedRows = 0;
        int sourceTerminatedRows = 0;
        int noGovernorateRows = 0;
        List<CaseEntity> casesToPersist = new ArrayList<>(rows.size());

        for (PlainteImportSharedRow row : rows) {
            if (eligibilityService.isSourceCompleted(row)) {
                sourceCompletedRows += 1;
            }
            if (eligibilityService.isSourceTerminated(row)) {
                sourceTerminatedRows += 1;
            }

            DistributionOutcome outcome = upsertComplaintCase(row, importedBy, context);
            casesToPersist.add(outcome.caseEntity());
            context.remember(outcome.caseEntity());

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
        }

        if (!casesToPersist.isEmpty()) {
            caseRepository.saveAll(casesToPersist);
        }

        int importedRows = createdCases + updatedExistingCases;
        int skippedRows = Math.max(0, sharedResult.totalRows() - importedRows);
        notificationEventService.onComplaintImportCompleted(
                importedBy,
                sharedResult.totalRows(),
                importedRows,
                skippedRows
        );

        return new AdminPlainteAnalyticsDistributionSummaryResponse(
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

    private ImportResolutionContext prepareContext(List<PlainteImportSharedRow> rows) {
        LinkedHashSet<String> crmCases = new LinkedHashSet<>();
        for (PlainteImportSharedRow row : rows) {
            if (row != null && PlainteAnalyticsSupport.hasText(row.crmCase())) {
                crmCases.add(row.crmCase());
            }
        }

        Map<String, CaseEntity> existingCasesByCrmCase = new LinkedHashMap<>();
        if (!crmCases.isEmpty()) {
            for (CaseEntity caseEntity : caseRepository.findByTypeAndCrmCaseIn(CaseType.PLAINTE, crmCases)) {
                if (caseEntity != null && PlainteAnalyticsSupport.hasText(caseEntity.getCrmCase())) {
                    existingCasesByCrmCase.put(caseEntity.getCrmCase(), caseEntity);
                }
            }
        }

        Map<String, List<AppUser>> sttsByLookup = new HashMap<>();
        for (AppUser stt : userRepository.findByRoleAndStatusAndEnabled(
                UserRole.ROLE_STT,
                AccountStatus.APPROVED,
                true
        )) {
            if (stt == null || stt.getRole() != UserRole.ROLE_STT) {
                continue;
            }

            registerSttLookup(sttsByLookup, normalizeLookup(stt.getFullName()), stt);
            registerSttLookup(sttsByLookup, normalizeLookup(stt.getEmail()), stt);
        }

        return new ImportResolutionContext(existingCasesByCrmCase, sttsByLookup);
    }

    private void registerSttLookup(Map<String, List<AppUser>> sttsByLookup, String lookupKey, AppUser stt) {
        if (!PlainteAnalyticsSupport.hasText(lookupKey) || stt == null) {
            return;
        }

        sttsByLookup.computeIfAbsent(lookupKey, ignored -> new ArrayList<>()).add(stt);
    }

    private DistributionOutcome upsertComplaintCase(
            PlainteImportSharedRow row,
            String importedBy,
            ImportResolutionContext context
    ) {
        CaseEntity existingCase = context.findExisting(row.crmCase());
        CaseEntity prepared = existingCase != null
                ? updateComplaintFromImport(existingCase, row, importedBy, context)
                : createComplaintFromImport(row, importedBy, context);

        return new DistributionOutcome(
                existingCase == null,
                PlainteAnalyticsSupport.hasText(prepared.getOwnerZmId()),
                !PlainteAnalyticsSupport.hasText(prepared.getGouvernorat()),
                prepared
        );
    }

    private CaseEntity createComplaintFromImport(
            PlainteImportSharedRow row,
            String importedBy,
            ImportResolutionContext context
    ) {
        Instant now = Instant.now();
        String normalizedGovernorate = resolveGovernorate(row.gouvernorat(), context);
        AppUser ownerZm = resolveOwnerZm(normalizedGovernorate, context);
        AppUser assignedStt = resolveStt(row.nameStt(), normalizedGovernorate, context);

        CaseEntity caseEntity = CaseEntity.builder()
                .type(CaseType.PLAINTE)
                .crmCase(row.crmCase())
                .dateCreationCrm(PlainteAnalyticsSupport.toInstant(row.dateCreationCrmDate()))
                .openingDateTimos(firstNonNull(
                        PlainteAnalyticsSupport.toInstant(row.openingDateSurTimosDate()),
                        PlainteAnalyticsSupport.toInstant(row.dateCreationCrmDate()),
                        now
                ))
                .status(CaseStatus.CREATED)
                .clientName(row.client())
                .clientPhone(row.contactClient())
                .contactCl2(row.contactCl2())
                .msisdn(row.msisdn())
                .commentaire(row.commentaire())
                .latitude(row.latitudeSite())
                .longitude(row.longitudeSite())
                .localite(row.localite())
                .delegation(row.delegation())
                .gouvernorat(normalizedGovernorate != null ? normalizedGovernorate : row.gouvernorat())
                .offre(row.offre())
                .desPack(row.desPack())
                .typeOffreSpeed(row.typeOffreSpeed())
                .typeClient(row.typeClient())
                .source(row.source())
                .complaintDetail(row.detail())
                .complaintDescription(row.description())
                .openDateActivation(PlainteAnalyticsSupport.toInstant(row.openDateActivationDate()))
                .sourceAssignedSttName(row.nameStt())
                .sourceStatut(row.statut())
                .sourceRepTravauxStt(row.repTravauxStt())
                .sourceGovernorate(row.gouvernorat())
                .sourceLastImportedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(importedBy)
                .build();

        applyOwnerAndStt(
                caseEntity,
                ownerZm,
                assignedStt,
                PlainteAnalyticsSupport.toInstant(row.dateAffectationSttDate()),
                now
        );
        applyImportedWorkflow(caseEntity, row);

        caseTimelineService.append(
                caseEntity,
                CaseTimelineEventType.CASE_CREATED,
                "Complaint case imported",
                "Complaint data was imported from the plainte analytics workflow.",
                importedBy,
                null,
                caseEntity.getStatus(),
                timelineData(
                        "crmCase", row.crmCase(),
                        "sourceFileType", "ANALYTICS_IMPORT",
                        "importedComplaint", true
                )
        );

        if (ownerZm != null) {
            caseTimelineService.append(
                    caseEntity,
                    CaseTimelineEventType.ASSIGNMENT_UPDATED,
                    "Owner ZM resolved",
                    "Governorate " + safe(caseEntity.getGouvernorat()) + " is owned by ZM " + safe(ownerZm.getFullName()),
                    importedBy,
                    caseEntity.getStatus(),
                    caseEntity.getStatus(),
                    timelineData(
                            "assigneeRole", "ROLE_ZM",
                            "assigneeEmail", ownerZm.getEmail(),
                            "automaticAssignment", true
                    )
            );
        }

        return caseEntity;
    }

    private CaseEntity updateComplaintFromImport(
            CaseEntity caseEntity,
            PlainteImportSharedRow row,
            String importedBy,
            ImportResolutionContext context
    ) {
        Instant now = Instant.now();
        caseEntity.setUpdatedAt(now);
        caseEntity.setSourceAssignedSttName(firstNonBlank(row.nameStt(), caseEntity.getSourceAssignedSttName()));
        caseEntity.setSourceStatut(firstNonBlank(row.statut(), caseEntity.getSourceStatut()));
        caseEntity.setSourceRepTravauxStt(firstNonBlank(row.repTravauxStt(), caseEntity.getSourceRepTravauxStt()));
        caseEntity.setSourceGovernorate(firstNonBlank(row.gouvernorat(), caseEntity.getSourceGovernorate()));
        caseEntity.setSourceLastImportedAt(now);

        fillIfBlank(caseEntity::getClientName, caseEntity::setClientName, row.client());
        fillIfBlank(caseEntity::getClientPhone, caseEntity::setClientPhone, row.contactClient());
        fillIfBlank(caseEntity::getContactCl2, caseEntity::setContactCl2, row.contactCl2());
        fillIfBlank(caseEntity::getCommentaire, caseEntity::setCommentaire, row.commentaire());
        fillIfBlank(caseEntity::getComplaintDetail, caseEntity::setComplaintDetail, row.detail());
        fillIfBlank(caseEntity::getComplaintDescription, caseEntity::setComplaintDescription, row.description());
        fillIfBlank(caseEntity::getLatitude, caseEntity::setLatitude, row.latitudeSite());
        fillIfBlank(caseEntity::getLongitude, caseEntity::setLongitude, row.longitudeSite());
        fillIfBlank(caseEntity::getDelegation, caseEntity::setDelegation, row.delegation());
        fillIfBlank(caseEntity::getLocalite, caseEntity::setLocalite, row.localite());
        fillIfBlank(caseEntity::getOffre, caseEntity::setOffre, row.offre());
        fillIfBlank(caseEntity::getDesPack, caseEntity::setDesPack, row.desPack());
        fillIfBlank(caseEntity::getTypeOffreSpeed, caseEntity::setTypeOffreSpeed, row.typeOffreSpeed());
        fillIfBlank(caseEntity::getTypeClient, caseEntity::setTypeClient, row.typeClient());
        fillIfBlank(caseEntity::getSource, caseEntity::setSource, row.source());

        if (!PlainteAnalyticsSupport.hasText(caseEntity.getGouvernorat())) {
            caseEntity.setGouvernorat(resolveGovernorate(row.gouvernorat(), context));
        }

        if (caseEntity.getOpenDateActivation() == null && row.openDateActivationDate() != null) {
            caseEntity.setOpenDateActivation(PlainteAnalyticsSupport.toInstant(row.openDateActivationDate()));
        }

        if (caseEntity.getStatus() == CaseStatus.CREATED
                || caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_ZM
                || caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_STT
                || caseEntity.getStatus() == CaseStatus.NOT_REACHABLE
                || caseEntity.getStatus() == CaseStatus.DIAGNOSIS_STEP) {
            applyImportedWorkflow(caseEntity, row);
        }

        if (caseEntity.getOwnerZmId() == null) {
            AppUser ownerZm = resolveOwnerZm(caseEntity.getGouvernorat(), context);
            if (ownerZm != null) {
                applyOwnerAndStt(caseEntity, ownerZm, null, null, now);
            }
        }

        if (caseEntity.getSttId() == null && firstNonBlank(row.nameStt(), null) != null) {
            AppUser assignedStt = resolveStt(row.nameStt(), caseEntity.getGouvernorat(), context);
            if (assignedStt != null) {
                applyOwnerAndStt(
                        caseEntity,
                        null,
                        assignedStt,
                        PlainteAnalyticsSupport.toInstant(row.dateAffectationSttDate()),
                        now
                );
            }
        }

        caseTimelineService.append(
                caseEntity,
                CaseTimelineEventType.STATUS_CHANGED,
                "Complaint source refreshed",
                "The plainte analytics import refreshed the latest source snapshot without recreating the case.",
                importedBy,
                caseEntity.getStatus(),
                caseEntity.getStatus(),
                timelineData(
                        "crmCase", row.crmCase(),
                        "sourceStatut", row.statut(),
                        "sourceResolution", row.notifResolution()
                )
        );

        return caseEntity;
    }

    private void applyOwnerAndStt(
            CaseEntity caseEntity,
            AppUser ownerZm,
            AppUser assignedStt,
            Instant importedSttAssignmentDate,
            Instant fallbackNow
    ) {
        if (ownerZm != null) {
            caseEntity.setOwnerZmId(ownerZm.getId());
            caseEntity.setOwnerZmName(ownerZm.getFullName());
            caseEntity.setZmId(ownerZm.getId());
            caseEntity.setZmEmail(ownerZm.getEmail());
            caseEntity.setZmName(ownerZm.getFullName());
            caseEntity.setDateAffectationZm(fallbackNow);
            caseEntity.setStatus(CaseStatus.ASSIGNED_TO_ZM);
        }

        if (assignedStt != null) {
            caseEntity.setAssignedSttId(assignedStt.getId());
            caseEntity.setSttId(assignedStt.getId());
            caseEntity.setSttEmail(assignedStt.getEmail());
            caseEntity.setSttName(assignedStt.getFullName());
            caseEntity.setDateAffectationStt(firstNonNull(importedSttAssignmentDate, fallbackNow, fallbackNow));
            caseEntity.setStatus(CaseStatus.ASSIGNED_TO_STT);
        }
    }

    private void applyImportedWorkflow(CaseEntity caseEntity, PlainteImportSharedRow row) {
        ComplaintResolutionInfo resolution = caseEntity.getResolution() != null
                ? caseEntity.getResolution()
                : ComplaintResolutionInfo.builder().build();
        resolution.setResolutionType(firstNonBlank(row.notifResolution(), resolution.getResolutionType()));
        caseEntity.setResolution(resolution);

        Boolean rdvReply = toBooleanFlag(row.repRdv());
        Instant rdvDate = PlainteAnalyticsSupport.toInstant(row.datePriseRdvDate());
        if (rdvReply != null || rdvDate != null || firstNonBlank(row.cmtRdv(), null) != null) {
            RdvInfo rdv = new RdvInfo();
            rdv.setJoignable(rdvReply == null || rdvReply);
            rdv.setDatePriseRdv(rdvDate);
            rdv.setDetail(row.detail());
            rdv.setCmtRdv(row.cmtRdv());
            caseEntity.setRdv(rdv);

            if (Boolean.FALSE.equals(rdvReply)) {
                caseEntity.setStatus(CaseStatus.NOT_REACHABLE);
            } else if (rdvDate != null) {
                caseEntity.setStatus(CaseStatus.DIAGNOSIS_STEP);
            }
        }

        if (firstNonBlank(row.repTravauxStt(), null) != null
                || row.dateFinTrvDate() != null
                || firstNonBlank(row.cmtTrvStt(), null) != null
                || firstNonBlank(row.notifResolution(), null) != null) {
            WorkInfo work = WorkInfo.builder()
                    .repTravauxStt(row.repTravauxStt())
                    .dateFinTrv(PlainteAnalyticsSupport.toInstant(row.dateFinTrvDate()))
                    .cmtTrvStt(row.cmtTrvStt())
                    .updatedAt(Instant.now())
                    .updatedBy("ANALYTICS_IMPORT")
                    .build();
            caseEntity.setWork(work);

            resolution.setResolutionComment(firstNonBlank(row.cmtTrvStt(), resolution.getResolutionComment()));
            resolution.setSubmittedAt(firstNonNull(PlainteAnalyticsSupport.toInstant(row.dateFinTrvDate()), Instant.now(), Instant.now()));
            resolution.setSubmittedBy("ANALYTICS_IMPORT");
            resolution.setValidationStatus(ResolutionValidationStatus.PENDING);
            caseEntity.setStatus(CaseStatus.RESOLUTION_SUBMITTED);
        }

        Boolean zmReply = toBooleanFlag(row.repZm());
        if (zmReply != null || firstNonBlank(row.cmtZm(), null) != null) {
            ZmValidation validation = ZmValidation.builder()
                    .approved(zmReply)
                    .cmtZm(row.cmtZm())
                    .validatedAt(Instant.now())
                    .validatedBy("ANALYTICS_IMPORT")
                    .build();
            caseEntity.setZmValidation(validation);
            resolution.setValidatedAt(Instant.now());
            resolution.setValidatedBy("ANALYTICS_IMPORT");

            if (Boolean.TRUE.equals(zmReply)) {
                resolution.setValidationStatus(ResolutionValidationStatus.APPROVED);
                caseEntity.setStatus(CaseStatus.CLOSED);
            } else if (Boolean.FALSE.equals(zmReply)) {
                resolution.setValidationStatus(ResolutionValidationStatus.RETURNED_TO_STT);
                caseEntity.setStatus(CaseStatus.RETURNED_TO_STT);
            }
        }

        CaseStatus derivedFromRawStatus = deriveStatusFromRawStatus(row.statut());
        if (derivedFromRawStatus != null
                && (caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_STT
                || caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_ZM
                || caseEntity.getStatus() == CaseStatus.CREATED)) {
            caseEntity.setStatus(derivedFromRawStatus);
        }
    }

    private AppUser resolveOwnerZm(String governorate, ImportResolutionContext context) {
        if (!PlainteAnalyticsSupport.hasText(governorate)) {
            return null;
        }

        if (context.ownerZmByGovernorate().containsKey(governorate)) {
            return context.ownerZmByGovernorate().get(governorate);
        }

        PlainteDistributionRoutingService.RoutingDecision decision = routingService.resolveZmForGovernorate(governorate);
        AppUser ownerZm = decision.zm();
        context.ownerZmByGovernorate().put(governorate, ownerZm);
        return ownerZm;
    }

    private AppUser resolveStt(String sttName, String governorate, ImportResolutionContext context) {
        String normalizedWanted = normalizeLookup(sttName);
        if (normalizedWanted == null) {
            return null;
        }

        String cacheKey = normalizedWanted + "|" + (governorate == null ? "" : governorate);
        if (context.sttByLookupAndGovernorate().containsKey(cacheKey)) {
            return context.sttByLookupAndGovernorate().get(cacheKey);
        }

        List<AppUser> candidates = context.sttsByLookup().getOrDefault(normalizedWanted, List.of());
        if (governorate != null) {
            candidates = candidates.stream()
                    .filter(user -> caseService.sttCoversGovernorateValue(user, governorate))
                    .toList();
        }

        AppUser selected = candidates.isEmpty() ? null : candidates.get(0);
        context.sttByLookupAndGovernorate().put(cacheKey, selected);
        return selected;
    }

    private String resolveGovernorate(String rawGovernorate, ImportResolutionContext context) {
        String cacheKey = rawGovernorate == null ? "" : rawGovernorate;
        if (context.governorateCache().containsKey(cacheKey)) {
            return context.governorateCache().get(cacheKey);
        }

        String canonical = PlainteAnalyticsSupport.canonicalGovernorate(rawGovernorate);
        String resolved;
        if (!PlainteAnalyticsSupport.hasText(canonical)) {
            resolved = PlainteAnalyticsSupport.cleanString(rawGovernorate);
        } else {
            try {
                resolved = caseService.normalizeGovernorateValue(canonical);
            } catch (ResponseStatusException ex) {
                resolved = canonical;
            }
        }

        context.governorateCache().put(cacheKey, resolved);
        return resolved;
    }

    private Boolean toBooleanFlag(String rawValue) {
        String normalized = normalizeLookup(rawValue);
        if (normalized == null) {
            return null;
        }

        if (normalized.equals("oui")
                || normalized.equals("yes")
                || normalized.equals("ok")
                || normalized.equals("approved")
                || normalized.equals("resolve")
                || normalized.equals("resolved")
                || normalized.equals("true")
                || normalized.equals("1")) {
            return true;
        }

        if (normalized.equals("non")
                || normalized.equals("no")
                || normalized.equals("ko")
                || normalized.equals("reject")
                || normalized.equals("rejected")
                || normalized.equals("false")
                || normalized.equals("0")) {
            return false;
        }

        return null;
    }

    private CaseStatus deriveStatusFromRawStatus(String rawStatus) {
        String normalized = normalizeLookup(rawStatus);
        if (normalized == null) {
            return null;
        }

        return switch (normalized) {
            case "created" -> CaseStatus.CREATED;
            case "assigned to zm", "assigned zm" -> CaseStatus.ASSIGNED_TO_ZM;
            case "assigned to stt", "assigned stt" -> CaseStatus.ASSIGNED_TO_STT;
            case "assigned to tech", "assigned tech" -> CaseStatus.ASSIGNED_TO_TECH;
            case "rdv", "rdv set", "rdv step", "diagnosis", "diagnosis step" -> CaseStatus.DIAGNOSIS_STEP;
            case "resolution submitted" -> CaseStatus.RESOLUTION_SUBMITTED;
            case "escalated", "escalation" -> CaseStatus.ESCALATED;
            case "returned to stt" -> CaseStatus.RETURNED_TO_STT;
            case "reopened" -> CaseStatus.REOPENED;
            case "closed" -> CaseStatus.CLOSED;
            default -> null;
        };
    }

    private String normalizeLookup(String value) {
        return PlainteAnalyticsSupport.normalizeLookupValue(value);
    }

    private <T> T firstNonNull(T primary, T fallback, T defaultValue) {
        if (primary != null) {
            return primary;
        }
        if (fallback != null) {
            return fallback;
        }
        return defaultValue;
    }

    private String firstNonBlank(String primary, String fallback) {
        return PlainteAnalyticsSupport.hasText(primary) ? primary : fallback;
    }

    private String safe(String value) {
        return PlainteAnalyticsSupport.hasText(value) ? value : "-";
    }

    private Map<String, Object> timelineData(Object... keyValuePairs) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (keyValuePairs == null) {
            return data;
        }

        for (int index = 0; index + 1 < keyValuePairs.length; index += 2) {
            if (keyValuePairs[index] instanceof String key && PlainteAnalyticsSupport.hasText(key) && keyValuePairs[index + 1] != null) {
                data.put(key, keyValuePairs[index + 1]);
            }
        }

        return data;
    }

    private void fillIfBlank(ValueReader reader, ValueWriter writer, String importedValue) {
        if (!PlainteAnalyticsSupport.hasText(importedValue) || PlainteAnalyticsSupport.hasText(reader.read())) {
            return;
        }
        writer.write(importedValue);
    }

    @FunctionalInterface
    private interface ValueReader {
        String read();
    }

    @FunctionalInterface
    private interface ValueWriter {
        void write(String value);
    }

    private record DistributionOutcome(
            boolean created,
            boolean assignedToZm,
            boolean noGovernorate,
            CaseEntity caseEntity
    ) {
    }

    private record ImportResolutionContext(
            Map<String, CaseEntity> existingCasesByCrmCase,
            Map<String, List<AppUser>> sttsByLookup,
            Map<String, String> governorateCache,
            Map<String, AppUser> ownerZmByGovernorate,
            Map<String, AppUser> sttByLookupAndGovernorate
    ) {
        private ImportResolutionContext(
                Map<String, CaseEntity> existingCasesByCrmCase,
                Map<String, List<AppUser>> sttsByLookup
        ) {
            this(
                    existingCasesByCrmCase,
                    sttsByLookup,
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>()
            );
        }

        private CaseEntity findExisting(String crmCase) {
            if (!PlainteAnalyticsSupport.hasText(crmCase)) {
                return null;
            }
            return existingCasesByCrmCase.get(crmCase);
        }

        private void remember(CaseEntity caseEntity) {
            if (caseEntity == null || !PlainteAnalyticsSupport.hasText(caseEntity.getCrmCase())) {
                return;
            }
            existingCasesByCrmCase.put(caseEntity.getCrmCase(), caseEntity);
        }
    }
}
