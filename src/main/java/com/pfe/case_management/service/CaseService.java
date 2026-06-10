package com.pfe.case_management.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.MainPackage.Entity.FileEntityType;
import com.pfe.MainPackage.Entity.FileMetadata;
import com.pfe.MainPackage.Entity.FileRole;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.AssignSttRequest;
import com.pfe.case_management.dto.AssignTechRequest;
import com.pfe.case_management.dto.BulkPltImportResult;
import com.pfe.case_management.dto.CaseProofFileResponse;
import com.pfe.case_management.dto.ComplaintDiagnosisRequest;
import com.pfe.case_management.dto.CompleteWorkRequest;
import com.pfe.case_management.dto.CreateCaseRequest;
import com.pfe.case_management.dto.PltCaseRow;
import com.pfe.case_management.dto.ReopenComplaintRequest;
import com.pfe.case_management.dto.SetRdvRequest;
import com.pfe.case_management.dto.ValidateZmRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ComplaintCategory;
import com.pfe.case_management.enums.ExecutionMode;
import com.pfe.case_management.enums.ExecutorRole;
import com.pfe.case_management.enums.ResolutionValidationStatus;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.ComplaintDiagnosisInfo;
import com.pfe.case_management.model.ComplaintResolutionInfo;
import com.pfe.case_management.model.RdvInfo;
import com.pfe.case_management.model.CaseTimelineEvent;
import com.pfe.case_management.model.TechnicalMetrics;
import com.pfe.case_management.model.UsedEquipmentItem;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.model.ZmValidation;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.stock_management.service.SttStockService;
import com.pfe.support.TunisiaGovernorateSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseService {
    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private static final String PLT_IMPORT_SOURCE = "PLT_TODO_IMPORT";

    private static final Set<CaseStatus> ASSIGN_STT_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_ZM,
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> COMPLAINT_ASSIGN_STT_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_ZM,
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.RETURNED_TO_STT,
        CaseStatus.REOPENED,
        CaseStatus.ESCALATED
    );

    private static final Set<CaseStatus> ASSIGN_TECH_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.NOT_REACHABLE,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> COMPLAINT_ASSIGN_TECH_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,   // PLT-imported complaints skip diagnosis and assign TECH directly
        CaseStatus.DIAGNOSIS_STEP,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RETURNED_TO_STT,
        CaseStatus.REOPENED
    );

    private static final Set<CaseStatus> SELF_EXECUTION_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.NOT_REACHABLE,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> COMPLAINT_SELF_EXECUTION_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.DIAGNOSIS_STEP,
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.RETURNED_TO_STT,
        CaseStatus.REOPENED
    );

    private static final Set<CaseStatus> SET_RDV_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.NOT_REACHABLE
    );

    private static final Set<CaseStatus> COMPLAINT_SET_RDV_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.NOT_REACHABLE,
        CaseStatus.DIAGNOSIS_STEP
    );

    private static final Set<CaseStatus> COMPLETE_WORK_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET
    );

    private static final Set<CaseStatus> COMPLAINT_COMPLETE_WORK_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.DIAGNOSIS_STEP,
        CaseStatus.RETURNED_TO_STT,
        CaseStatus.REOPENED
    );

    private static final Set<CaseStatus> WORK_PROOF_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.WORK_DONE,
        CaseStatus.REJECTED_BY_ZM,
        CaseStatus.VALIDATED_BY_ZM
    );

    private static final Set<CaseStatus> COMPLAINT_WORK_PROOF_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.DIAGNOSIS_STEP,
        CaseStatus.RESOLUTION_SUBMITTED,
        CaseStatus.RETURNED_TO_STT,
        CaseStatus.VALIDATED_BY_ZM
    );

    private final CaseRepository repository;
    private final UserRepository userRepository;
    private final SlaTrackingService slaTrackingService;
    private final NotificationEventService notificationEventService;
    private final CaseTimelineService caseTimelineService;
    private final FileStorageService fileStorageService;
    private final CasePdfExportService casePdfExportService;
    private final SttStockService sttStockService;

    public CaseEntity createCase(CreateCaseRequest request) {
        ensureCurrentUserHasRole("ROLE_ADMIN");

        if (repository.findByCrmCase(request.crmCase()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CRM case already exists");
        }

        Instant now = Instant.now();
        String governorate = normalizeGovernorate(request.gouvernorat());
        AppUser ownerZm = resolveOwnerZm(governorate, request.type());

        CaseEntity entity = CaseEntity.builder()
                .type(request.type())
                .crmCase(request.crmCase())
                .dateCreationCrm(request.dateCreationCrm())
                .openingDateTimos(request.openingDateTimos())
                .status(CaseStatus.ASSIGNED_TO_ZM)
                .clientName(request.clientName())
                .clientPhone(request.clientPhone())
                .msisdn(request.msisdn())
                .commentaire(request.commentaire())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .localite(request.localite())
                .delegation(request.delegation())
                .gouvernorat(governorate)
                .siteSecteur(request.siteSecteur())
                .ownerZmId(ownerZm.getId())
                .ownerZmName(ownerZm.getFullName())
                .offre(request.offre())
                .desPack(request.desPack())
                .zmId(ownerZm.getId())
                .zmEmail(ownerZm.getEmail())
                .zmName(ownerZm.getFullName())
                .dateAffectationZm(now)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(getCurrentUsername())
                .build();

        caseTimelineService.append(
                entity,
                CaseTimelineEventType.CASE_CREATED,
                "Case created",
                "New " + caseTypeLabel(entity.getType()).toLowerCase() + " captured from CRM",
                getCurrentUsername(),
                null,
                entity.getStatus(),
                timelineData(
                    "crmCase", request.crmCase(),
                    "type", request.type() != null ? request.type().name() : null
                )
            );

        caseTimelineService.append(
                entity,
                CaseTimelineEventType.ASSIGNMENT_UPDATED,
                "Owner ZM resolved",
                "Governorate " + governorate + " is owned by ZM " + safe(ownerZm.getFullName(), ownerZm.getEmail()),
                getCurrentUsername(),
                entity.getStatus(),
                entity.getStatus(),
                timelineData(
                        "assigneeRole", "ROLE_ZM",
                        "assigneeEmail", ownerZm.getEmail(),
                        "governorate", governorate,
                        "automaticAssignment", true
                )
        );

        CaseEntity saved = repository.save(entity);
        notificationEventService.onCaseAssignedToZm(saved, getCurrentUsername());
        return saved;
    }

    public List<CaseEntity> getAllCasesByType(CaseType type) {
        List<CaseEntity> scoped;

        if (currentUserHasRole("ROLE_ADMIN")) {
            scoped = repository.findByType(type);
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            sanitizeCaseListForCurrentViewer(scoped);
            return scoped;
        }

        if (currentUserHasRole("ROLE_ZM")) {
            scoped = repository.findByTypeAndZmEmailIgnoreCase(type, getCurrentUsername());
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            sanitizeCaseListForCurrentViewer(scoped);
            return scoped;
        }

        if (currentUserHasRole("ROLE_STT")) {
            scoped = repository.findByTypeAndSttEmailIgnoreCase(type, getCurrentUsername());
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            sanitizeCaseListForCurrentViewer(scoped);
            return scoped;
        }

        if (currentUserHasRole("ROLE_TECH")) {
            scoped = repository.findByTypeAndTechEmailIgnoreCase(type, getCurrentUsername());
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            sanitizeCaseListForCurrentViewer(scoped);
            return scoped;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * Creates complaint cases (CaseType.PLAINTE) in bulk from PLT Excel rows.
     * Called by TodoImportService when businessType=PLT; no ROLE_ADMIN guard —
     * the caller authenticates as ROLE_ZM and passes the resolved user directly.
     */
    public BulkPltImportResult bulkImportPltComplaintCases(
            List<PltCaseRow> rows,
            AppUser connectedZm,
            String batchId,
            String sourceFileName
    ) {
        if (rows == null || rows.isEmpty()) {
            return new BulkPltImportResult(0, 0, 0);
        }

        Instant now = Instant.now();

        // Bulk duplicate check against existing PLAINTE cases
        List<String> candidateCrmCases = rows.stream()
                .map(PltCaseRow::crmCase)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();

        Set<String> existingCrmCases = candidateCrmCases.isEmpty() ? Set.of()
                : repository.findByTypeAndCrmCaseIn(CaseType.PLAINTE, candidateCrmCases)
                        .stream()
                        .map(CaseEntity::getCrmCase)
                        .filter(c -> c != null && !c.isBlank())
                        .collect(Collectors.toSet());

        Set<String> seenInBatch = new LinkedHashSet<>();
        int created = 0;
        int duplicates = 0;
        int invalid = 0;

        for (PltCaseRow row : rows) {
            if (row.crmCase() == null || row.crmCase().isBlank()) {
                log.warn("PLT import: skipping row {} with missing CRM_CASE", row.rowNumber());
                invalid++;
                continue;
            }

            // A missing gouvernorat means the case can never have an STT assigned
            // (sttCoversGovernorate checks isBlank(gouvernorat) → false immediately).
            // Skip rather than creating a permanently stuck case.
            if (row.gouvernorat() == null || row.gouvernorat().isBlank()) {
                log.warn("PLT import: skipping row {} crmCase={} — gouvernorat is blank", row.rowNumber(), row.crmCase());
                invalid++;
                continue;
            }

            String trimmedCrm = row.crmCase().trim();

            if (!seenInBatch.add(trimmedCrm)) {
                duplicates++;
                continue;
            }

            if (existingCrmCases.contains(trimmedCrm)) {
                duplicates++;
                continue;
            }

            try {
                CaseEntity complaint = buildPltComplaintEntity(row, connectedZm, batchId, sourceFileName, now);
                CaseEntity saved = repository.save(complaint);
                try {
                    notificationEventService.onCaseAssignedToZm(saved, connectedZm.getEmail());
                } catch (Exception notifEx) {
                    log.warn("PLT import: notification failed for case crmCase={}: {}", trimmedCrm, notifEx.getMessage());
                }
                created++;
            } catch (Exception ex) {
                log.warn("PLT import: failed to create complaint case crmCase={}: {}", trimmedCrm, ex.getMessage());
                invalid++;
            }
        }

        log.info("plt_bulk_import batchId={} sourceFile={} zmEmail={} rows={} created={} duplicates={} invalid={}",
                batchId, sourceFileName, connectedZm.getEmail(), rows.size(), created, duplicates, invalid);

        return new BulkPltImportResult(created, duplicates, invalid);
    }

    private CaseEntity buildPltComplaintEntity(
            PltCaseRow row,
            AppUser connectedZm,
            String batchId,
            String sourceFileName,
            Instant now
    ) {
        Instant openingDateTimos = row.openDateSurTimosDate() != null
                ? row.openDateSurTimosDate().atStartOfDay().toInstant(ZoneOffset.UTC)
                : null;

        // Try to normalize the governorate; fall back to canonical value from analytics
        String gouvernorat = row.gouvernorat();
        if (!isBlank(gouvernorat)) {
            try {
                gouvernorat = TunisiaGovernorateSupport.requireSupported(gouvernorat);
            } catch (IllegalArgumentException | ResponseStatusException ignored) {
                // Keep canonical value already set by TodoAnalyticsSupport
            }
        }

        // DMS coordinate conversion using the same utility used in todo import
        Double decimalLat = TodoAnalyticsSupport.parseCoordinateToDecimal(row.rawLatitude());
        Double decimalLon = TodoAnalyticsSupport.parseCoordinateToDecimal(row.rawLongitude());

        CaseEntity complaint = CaseEntity.builder()
                .type(CaseType.PLAINTE)
                .crmCase(row.crmCase().trim())
                .openingDateTimos(openingDateTimos)
                .status(CaseStatus.ASSIGNED_TO_ZM)
                .clientPhone(row.contact1())
                .contactCl2(row.contact2())
                .msisdn(row.msisdn())
                .gouvernorat(gouvernorat)
                .latitude(decimalLat != null ? String.valueOf(decimalLat) : row.normalizedLatitude())
                .longitude(decimalLon != null ? String.valueOf(decimalLon) : row.normalizedLongitude())
                .complaintDetail(row.description())
                .complaintDescription(row.description())
                .ownerZmId(connectedZm.getId())
                .ownerZmName(connectedZm.getFullName())
                .zmId(connectedZm.getId())
                .zmEmail(connectedZm.getEmail())
                .zmName(connectedZm.getFullName())
                .dateAffectationZm(now)
                .sourceAssignedSttName(row.sttFromExcel())
                .sourceStatut(row.statutFromExcel())
                .sourceGovernorate(row.gouvernorat())
                .sourceLastImportedAt(now)
                .source(PLT_IMPORT_SOURCE)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(connectedZm.getEmail())
                .build();

        caseTimelineService.append(
                complaint,
                CaseTimelineEventType.CASE_CREATED,
                "Complaint imported from PLT file",
                "Imported from " + sourceFileName + " (batch " + batchId + ")."
                        + " STATUT = En cours, ZONE_MNANAGER matched " + safe(connectedZm.getFullName(), connectedZm.getEmail()) + ".",
                connectedZm.getEmail(),
                null,
                CaseStatus.ASSIGNED_TO_ZM,
                timelineData(
                        "source", PLT_IMPORT_SOURCE,
                        "importBatchId", batchId,
                        "crmCase", row.crmCase().trim()
                )
        );

        caseTimelineService.append(
                complaint,
                CaseTimelineEventType.ASSIGNMENT_UPDATED,
                "Assigned to Zone Manager",
                "Automatically assigned to ZM " + safe(connectedZm.getFullName(), connectedZm.getEmail()) + " from PLT import.",
                connectedZm.getEmail(),
                null,
                CaseStatus.ASSIGNED_TO_ZM,
                timelineData(
                        "assigneeRole", "ROLE_ZM",
                        "assigneeEmail", connectedZm.getEmail()
                )
        );

        return complaint;
    }

    public CaseEntity getCaseById(String caseId) {
        CaseEntity c = getCaseOrThrow(caseId);

        if (currentUserHasRole("ROLE_ADMIN")) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return sanitizeCaseForCurrentViewer(c);
        }

        String current = getCurrentUsername();

        if (currentUserHasRole("ROLE_ZM") && equalsIgnoreCase(c.getZmEmail(), current)) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return sanitizeCaseForCurrentViewer(c);
        }

        if (currentUserHasRole("ROLE_STT") && equalsIgnoreCase(c.getSttEmail(), current)) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return sanitizeCaseForCurrentViewer(c);
        }

        if (currentUserHasRole("ROLE_TECH") && equalsIgnoreCase(c.getTechEmail(), current)) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return sanitizeCaseForCurrentViewer(c);
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this case");
    }

    public byte[] exportCasePdf(String caseId) {
        CaseEntity caseEntity = getCaseById(caseId);
        return casePdfExportService.generateCasePdf(caseEntity);
    }

    public String buildCasePdfFilename(String caseId) {
        CaseEntity caseEntity = getCaseById(caseId);
        return casePdfExportService.buildFilename(caseEntity);
    }

    public CaseEntity assignToStt(String caseId, AssignSttRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String triggeredBy = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanActAsAssignedZm(c);
        ensureStatusIn(
            c,
            assignSttAllowedStatuses(c),
            "Case cannot be assigned to STT in current state"
        );

        if (isBlank(c.getOwnerZmId()) && isBlank(c.getZmId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case must have an owner ZM before STT assignment");
        }

        AppUser stt = userRepository.findById(request.sttId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "STT not found"));

        ensureUserHasRole(stt, UserRole.ROLE_STT, "Selected user is not an STT");
        ensureUserIsActive(stt, "Selected STT is not active");

        if (!sttCoversGovernorate(stt, c.getGouvernorat())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This STT does not cover the case governorate"
            );
        }

        boolean sttChanged = !equalsSafe(c.getSttId(), stt.getId());

        c.setAssignedSttId(stt.getId());
        c.setSttId(stt.getId());
        c.setSttEmail(stt.getEmail());
        c.setSttName(stt.getFullName());
        c.setDateAffectationStt(Instant.now());

        if (sttChanged) {
            resetExecutionState(c);
        }

        c.setStatus(CaseStatus.ASSIGNED_TO_STT);
        c.setUpdatedAt(Instant.now());

        caseTimelineService.append(
            c,
            CaseTimelineEventType.ASSIGNMENT_UPDATED,
            "STT assignment updated",
            "Case assigned to STT " + safe(c.getSttName(), c.getSttEmail()),
            triggeredBy,
            previousStatus,
            c.getStatus(),
            timelineData(
                "assigneeRole", "ROLE_STT",
                "assigneeEmail", c.getSttEmail(),
                "resetTechAssignment", sttChanged
            )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        notificationEventService.onCaseAssignedToStt(saved, triggeredBy);
        return sanitizeCaseForCurrentViewer(saved);
    }

    public CaseEntity assignToTech(String caseId, AssignTechRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String triggeredBy = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanActAsAssignedStt(c);
        ensureStatusIn(
            c,
            assignTechAllowedStatuses(c),
            "Case cannot be assigned to TECH in current state"
        );

        // PLT-imported complaints skip the mandatory diagnosis step and go directly to TECH like ACT cases.
        if (isComplaint(c) && !isPltImportSource(c)) {
            ensureComplaintRequiresFieldExecution(c, "A complaint must be diagnosed for field intervention before TECH assignment");
        }

        if (isBlank(c.getSttId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case must be assigned to an STT first");
        }

        AppUser tech = userRepository.findById(request.techId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TECH not found"));

        ensureUserHasRole(tech, UserRole.ROLE_TECH, "Selected user is not a TECH");
        ensureUserIsActive(tech, "Selected technician is not active");

        if (!equalsSafe(tech.getAssignedSttId(), c.getSttId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This technician does not belong to the assigned STT"
            );
        }

        c.setTechId(tech.getId());
        c.setTechEmail(tech.getEmail());
        c.setTechName(tech.getFullName());
        Instant assignmentTime = Instant.now();
        c.setDateAffectationTech(assignmentTime);
        c.setExecutorId(tech.getId());
        c.setExecutorRole(ExecutorRole.TECH);
        c.setExecutionMode(ExecutionMode.TECH_EXECUTION);

        boolean preserveProgressState = previousStatus == CaseStatus.RDV_SET || previousStatus == CaseStatus.NOT_REACHABLE;
        boolean startRdvSla = !isComplaintDiagnosisDrivenExecution(c, previousStatus);
        if (!preserveProgressState) {
            c.setStatus(CaseStatus.ASSIGNED_TO_TECH);
            if (startRdvSla) {
                slaTrackingService.startRdvSla(c, assignmentTime);
            }
        }

        c.setUpdatedAt(assignmentTime);

        caseTimelineService.append(
            c,
            CaseTimelineEventType.ASSIGNMENT_UPDATED,
            "TECH assignment updated",
            "Case assigned to TECH " + safe(c.getTechName(), c.getTechEmail()),
            triggeredBy,
            previousStatus,
            c.getStatus(),
            timelineData(
                "assigneeRole", "ROLE_TECH",
                "assigneeEmail", c.getTechEmail(),
                "executorRole", ExecutorRole.TECH.name(),
                "executionMode", ExecutionMode.TECH_EXECUTION.name()
            )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        notificationEventService.onCaseAssignedToTech(saved, triggeredBy);
        return sanitizeCaseForCurrentViewer(saved);
    }

    public CaseEntity executeMyself(String caseId) {
        CaseEntity c = getCaseOrThrow(caseId);
        String triggeredBy = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanActAsAssignedStt(c);
        ensureStatusIn(
                c,
                selfExecutionAllowedStatuses(c),
                "Case cannot switch to STT self-execution in current state"
        );

        // PLT-imported complaints skip the mandatory diagnosis step — STT may self-execute directly.
        if (isComplaint(c) && !isPltImportSource(c)) {
            ensureComplaintRequiresFieldExecution(c, "A complaint must be diagnosed for field intervention before STT self-execution");
        }

        if (isBlank(c.getSttId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case must be assigned to an STT first");
        }

        Instant switchTime = Instant.now();
        clearTechAssignment(c);
        c.setExecutorId(c.getSttId());
        c.setExecutorRole(ExecutorRole.STT);
        c.setExecutionMode(ExecutionMode.STT_SELF_EXECUTION);

        boolean preserveProgressState = previousStatus == CaseStatus.RDV_SET || previousStatus == CaseStatus.NOT_REACHABLE;
        boolean startRdvSla = !isComplaintDiagnosisDrivenExecution(c, previousStatus);
        if (!preserveProgressState) {
            c.setStatus(CaseStatus.ASSIGNED_TO_STT);
            if (startRdvSla) {
                slaTrackingService.startRdvSla(c, switchTime);
            }
        }

        c.setUpdatedAt(switchTime);

        caseTimelineService.append(
                c,
                CaseTimelineEventType.ASSIGNMENT_UPDATED,
                "Execution switched to STT",
                "Assigned STT " + safe(c.getSttName(), c.getSttEmail()) + " will execute this case directly",
                triggeredBy,
                previousStatus,
                c.getStatus(),
                timelineData(
                        "assigneeRole", "ROLE_STT",
                        "assigneeEmail", c.getSttEmail(),
                        "executorRole", ExecutorRole.STT.name(),
                        "executionMode", ExecutionMode.STT_SELF_EXECUTION.name()
                )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return sanitizeCaseForCurrentViewer(saved);
    }

    public CaseEntity setRdv(String caseId, SetRdvRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanExecuteCase(c);

        ensureStatusIn(c, setRdvAllowedStatuses(c), "RDV update is not allowed in current state");

        RdvInfo rdv = new RdvInfo();
        rdv.setJoignable(request.joignable());
        rdv.setDatePriseRdv(request.datePriseRdv());
        rdv.setCauseInjoignabilite(request.causeInjoignabilite());
        rdv.setDetail(request.detail());
        rdv.setCmtRdv(request.cmtRdv());
        rdv.setAbandon(request.abandon());
        rdv.setCauseAbandon(request.causeAbandon());

        c.setRdv(rdv);
        boolean triggerImmediateBreachNotification = false;

        if (!request.joignable()) {
            c.setStatus(CaseStatus.NOT_REACHABLE);
            slaTrackingService.refreshLiveState(c, Instant.now());
        } else if (request.abandon()) {
            c.setStatus(CaseStatus.ABANDONED);
            slaTrackingService.forceBreach(c, Instant.now());
            if (c.getSlaTracking() != null && !c.getSlaTracking().isBreachNotificationSent()) {
                c.getSlaTracking().setBreachNotificationSent(true);
                triggerImmediateBreachNotification = true;
            }
        } else {
            c.setStatus(isComplaint(c) ? CaseStatus.DIAGNOSIS_STEP : CaseStatus.RDV_SET);
            Instant rdvSetAt = Instant.now();
            slaTrackingService.completeOnRdv(c, rdvSetAt);

            if (c.getSlaTracking() != null
                    && c.getSlaTracking().getStatus() == SlaStatus.COMPLETED_LATE
                    && !c.getSlaTracking().isBreachNotificationSent()) {
                c.getSlaTracking().setBreachNotificationSent(true);
                triggerImmediateBreachNotification = true;
            }
        }

        c.setUpdatedAt(Instant.now());

        caseTimelineService.append(
            c,
            CaseTimelineEventType.RDV_UPDATED,
            "RDV details updated",
            rdvTimelineDetails(request),
            actor,
            previousStatus,
            c.getStatus(),
            timelineData(
                "joignable", request.joignable(),
                "abandon", request.abandon(),
                "rdvAt", request.datePriseRdv()
            )
        );

        if (triggerImmediateBreachNotification) {
            caseTimelineService.append(
                c,
                CaseTimelineEventType.SLA_BREACHED,
                "SLA breached",
                "RDV update caused an immediate SLA breach condition",
                "SYSTEM",
                c.getStatus(),
                c.getStatus(),
                timelineData("source", "RDV_UPDATE")
            );
        }

        CaseEntity saved = repository.save(c);

        if (triggerImmediateBreachNotification) {
            notificationEventService.onSlaBreached(saved);
        }

        return sanitizeCaseForCurrentViewer(saved);
    }

    @Transactional
    public CaseEntity completeWork(String caseId, CompleteWorkRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();
        Instant now = Instant.now();

        ensureCurrentUserCanExecuteCase(c);

        ensureStatusIn(c, completeWorkAllowedStatuses(c), "Work completion is not allowed in current state");
        // PLT-imported complaints skip the diagnosis prerequisite — work can be completed like ACT.
        if (isComplaint(c) && !isPltImportSource(c)) {
            ensureComplaintRequiresFieldExecution(c, "Complaint work can only be submitted after a diagnosis requiring field intervention");
        }
        validateLocationSnapshot(
                request.locationLatitude(),
                request.locationLongitude(),
                "Work submission location is invalid"
        );

        AppUser executingTechnician = null;
        List<UsedEquipmentItem> usedEquipment = List.of();
        if (isTechExecution(c)) {
            executingTechnician = resolveAssignedTechnician(c);
            usedEquipment = sttStockService.syncCaseUsageForWorkSubmission(
                    c,
                    executingTechnician,
                    request.usedEquipment(),
                    actor
            );
        } else if (isSttSelfExecution(c) && request.usedEquipment() != null && !request.usedEquipment().isEmpty()) {
            AppUser executingStt = resolveAssignedStt(c);
            usedEquipment = sttStockService.syncSttWarehouseUsageForCase(
                    c,
                    executingStt,
                    request.usedEquipment(),
                    actor
            );
            executingTechnician = executingStt;
        } else if (request.usedEquipment() != null && !request.usedEquipment().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Used equipment can only be submitted when the active executor is a technician or STT in self-execution mode"
            );
        }

        WorkInfo work = c.getWork() != null ? c.getWork() : new WorkInfo();
        work.setRepTravauxStt(request.repTravauxStt());
        work.setDateFinTrv(request.dateFinTrv());
        work.setCmtTrvStt(request.cmtTrvStt());
        work.setUpdatedAt(now);
        work.setUpdatedBy(actor);
        work.setLocationLatitude(request.locationLatitude());
        work.setLocationLongitude(request.locationLongitude());
        work.setLocationCapturedAt(request.locationCapturedAt());
        work.setLocationAccuracyMeters(request.locationAccuracyMeters());
        work.setUsedEquipment(usedEquipment);
        work.setStockAppliedTechId(
                executingTechnician != null && !usedEquipment.isEmpty() ? executingTechnician.getId() : null
        );

        TechnicalMetrics metrics = new TechnicalMetrics();
        metrics.setMetrageCable(request.metrageCable());
        metrics.setNombrePv(request.nombrePv());
        metrics.setDebitDl(request.debitDl());
        metrics.setDebitUl(request.debitUl());
        metrics.setRsrp(request.rsrp());
        metrics.setRsrq(request.rsrq());
        metrics.setSinr(request.sinr());

        c.setWork(work);
        c.setMetrics(metrics);
        if (isComplaint(c)) {
            ComplaintResolutionInfo resolution = ensureComplaintResolution(c);
            resolution.setResolutionComment(trimToNull(request.cmtTrvStt()));
            resolution.setSubmittedAt(now);
            resolution.setSubmittedBy(actor);
            resolution.setValidationStatus(ResolutionValidationStatus.PENDING);
            c.setStatus(CaseStatus.RESOLUTION_SUBMITTED);
        } else {
            c.setStatus(CaseStatus.WORK_DONE);
        }
        c.setUpdatedAt(now);

        caseTimelineService.append(
            c,
            CaseTimelineEventType.WORK_UPDATED,
            "Work report submitted",
            "Intervention details and technical metrics were updated",
            actor,
            previousStatus,
            c.getStatus(),
            timelineData(
                "workResult", request.repTravauxStt(),
                "dateFinTrv", request.dateFinTrv(),
                "debitDl", request.debitDl(),
                "debitUl", request.debitUl(),
                "locationLatitude", request.locationLatitude(),
                "locationLongitude", request.locationLongitude(),
                "locationCapturedAt", request.locationCapturedAt(),
                "locationAccuracyMeters", request.locationAccuracyMeters(),
                "usedEquipmentCount", usedEquipment.size(),
                "usedEquipmentQuantity", usedEquipment.stream().mapToInt(UsedEquipmentItem::getQuantity).sum()
            )
        );

        return sanitizeCaseForCurrentViewer(repository.save(c));
    }

    public CaseEntity uploadWorkProof(
            String caseId,
            MultipartFile file,
            Double latitude,
            Double longitude,
            Instant locationCapturedAt,
            Double locationAccuracyMeters,
            String captureSource
    ) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();

        ensureCurrentUserCanExecuteCase(c);

        ensureStatusIn(c, workProofAllowedStatuses(c), "Work proof upload is not allowed in current state");
        validateLocationSnapshot(latitude, longitude, "Work proof location is invalid");

        FileMetadata metadata = fileStorageService.upload(
                file,
                FileEntityType.CASE,
                c.getId(),
                FileRole.WORK_PROOF,
                actor,
                latitude,
                longitude,
                locationCapturedAt,
                locationAccuracyMeters,
                captureSource
        );

        c.setUpdatedAt(Instant.now());
        caseTimelineService.append(
                c,
                CaseTimelineEventType.WORK_UPDATED,
                "Work proof uploaded",
                "Proof image " + safe(metadata.getOriginalFilename(), null) + " was attached to the case",
                actor,
                c.getStatus(),
                c.getStatus(),
                timelineData(
                        "proofFileId", metadata.getId(),
                        "proofFilename", metadata.getOriginalFilename(),
                        "locationLatitude", metadata.getLocationLatitude(),
                        "locationLongitude", metadata.getLocationLongitude(),
                        "locationCapturedAt", metadata.getLocationCapturedAt(),
                        "locationAccuracyMeters", metadata.getLocationAccuracyMeters(),
                        "captureSource", metadata.getCaptureSource()
                )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return sanitizeCaseForCurrentViewer(saved);
    }

    @Transactional
    public CaseEntity validateByZm(String caseId, ValidateZmRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanActAsAssignedZm(c);
        ensureStatusIn(c, validateByZmAllowedStatuses(c), "ZM validation is only allowed after work completion");

        if (!Boolean.TRUE.equals(request.approved())) {
            sttStockService.releaseCaseUsageReservation(
                    c,
                    actor,
                    "zm_rejected_before_final_validation",
                    true
            );
        }

        ZmValidation validation = new ZmValidation();
        validation.setApproved(Boolean.TRUE.equals(request.approved()));
        validation.setCmtZm(request.comment());
        validation.setValidatedAt(Instant.now());
        validation.setValidatedBy(getCurrentUsername());

        c.setZmValidation(validation);

        if (Boolean.TRUE.equals(request.approved())) {
            c.setStatus(CaseStatus.VALIDATED_BY_ZM);
        } else {
            c.setStatus(isComplaint(c) ? CaseStatus.RETURNED_TO_STT : CaseStatus.REJECTED_BY_ZM);
        }

        if (isComplaint(c)) {
            ComplaintResolutionInfo resolution = ensureComplaintResolution(c);
            resolution.setValidatedAt(Instant.now());
            resolution.setValidatedBy(actor);
            resolution.setValidationStatus(Boolean.TRUE.equals(request.approved())
                    ? ResolutionValidationStatus.APPROVED
                    : ResolutionValidationStatus.RETURNED_TO_STT);
            if (Boolean.TRUE.equals(request.approved())) {
                resolution.setResolutionComment(firstNonBlank(request.comment(), resolution.getResolutionComment()));
            }
        }

        c.setUpdatedAt(Instant.now());

        caseTimelineService.append(
            c,
            CaseTimelineEventType.ZM_VALIDATION,
            Boolean.TRUE.equals(request.approved()) ? "Case approved by ZM" : "Case rejected by ZM",
            request.comment(),
            actor,
            previousStatus,
            c.getStatus(),
            timelineData("approved", Boolean.TRUE.equals(request.approved()))
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return sanitizeCaseForCurrentViewer(saved);
    }

    public CaseEntity closeCase(String caseId) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanActAsAssignedZm(c);

        if (c.getStatus() != CaseStatus.VALIDATED_BY_ZM) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only a case validated by ZM can be closed"
            );
        }

        c.setStatus(CaseStatus.CLOSED);
        c.setUpdatedAt(Instant.now());

        caseTimelineService.append(
            c,
            CaseTimelineEventType.CASE_CLOSED,
            "Case closed",
            "Case was finalized after ZM validation",
            actor,
            previousStatus,
            c.getStatus(),
            Map.of()
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return sanitizeCaseForCurrentViewer(saved);
    }

    public CaseEntity submitComplaintDiagnosis(String caseId, ComplaintDiagnosisRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        ensureComplaintCase(c);

        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();
        Instant now = Instant.now();

        ensureCurrentUserCanActAsAssignedStt(c);
        ensureStatusIn(
                c,
                EnumSet.of(
                        CaseStatus.ASSIGNED_TO_STT,
                        CaseStatus.DIAGNOSIS_STEP,
                        CaseStatus.RETURNED_TO_STT,
                        CaseStatus.REOPENED
                ),
                "Diagnosis is not allowed in the current complaint state"
        );

        if (!hasCompletedComplaintRdv(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diagnosis is only available after the complaint RDV step");
        }

        ComplaintDiagnosisInfo diagnosis = ComplaintDiagnosisInfo.builder()
                .complaintCategory(request.complaintCategory())
                .rootCause(trimToNull(request.rootCause()))
                .diagnosisComment(trimToNull(request.diagnosisComment()))
                .requiresFieldWork(request.requiresFieldWork())
                .requiresTechIntervention(request.requiresTechIntervention())
                .requiresEscalation(request.requiresEscalation())
                .diagnosedAt(now)
                .diagnosedBy(actor)
                .build();

        c.setDiagnosis(diagnosis);

        if (request.requiresEscalation()) {
            clearTechAssignment(c);
            clearExecutionDecision(c);
            c.setStatus(CaseStatus.ESCALATED);
        } else if (!request.requiresFieldWork() && !request.requiresTechIntervention()) {
            ComplaintResolutionInfo resolution = ensureComplaintResolution(c);
            resolution.setResolutionComment(trimToNull(request.diagnosisComment()));
            resolution.setSubmittedAt(now);
            resolution.setSubmittedBy(actor);
            resolution.setValidationStatus(ResolutionValidationStatus.PENDING);
            c.setStatus(CaseStatus.RESOLUTION_SUBMITTED);
        } else {
            if (request.requiresTechIntervention()) {
                clearTechAssignment(c);
                clearExecutionDecision(c);
            } else {
                // STT will self-execute — set executor now so work can be submitted immediately after diagnosis.
                c.setExecutorId(c.getSttId());
                c.setExecutorRole(ExecutorRole.STT);
                c.setExecutionMode(ExecutionMode.STT_SELF_EXECUTION);
            }
            c.setStatus(CaseStatus.DIAGNOSIS_STEP);
        }

        c.setUpdatedAt(now);

        caseTimelineService.append(
                c,
                CaseTimelineEventType.DIAGNOSIS_UPDATED,
                "Complaint diagnosis updated",
                firstNonBlank(request.diagnosisComment(), "Complaint diagnosis was captured"),
                actor,
                previousStatus,
                c.getStatus(),
                timelineData(
                        "complaintCategory", request.complaintCategory() != null ? request.complaintCategory().name() : ComplaintCategory.OTHER.name(),
                        "requiresFieldWork", request.requiresFieldWork(),
                        "requiresTechIntervention", request.requiresTechIntervention(),
                        "requiresEscalation", request.requiresEscalation()
                )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return sanitizeCaseForCurrentViewer(saved);
    }

    @Transactional
    public CaseEntity reopenComplaintCase(String caseId, ReopenComplaintRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        ensureComplaintCase(c);

        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();
        Instant now = Instant.now();

        ensureCurrentUserCanActAsAssignedZm(c);
        ensureStatusIn(
                c,
                EnumSet.of(CaseStatus.CLOSED, CaseStatus.VALIDATED_BY_ZM, CaseStatus.RESOLUTION_SUBMITTED),
                "Only resolved complaints can be reopened"
        );

        sttStockService.releaseCaseUsageReservation(
                c,
                actor,
                previousStatus == CaseStatus.RESOLUTION_SUBMITTED
                        ? "complaint_reopened_before_final_validation"
                        : "complaint_reopened_after_validation",
                previousStatus == CaseStatus.RESOLUTION_SUBMITTED
        );

        c.setStatus(CaseStatus.REOPENED);
        clearTechAssignment(c);
        clearExecutionDecision(c);
        c.setUpdatedAt(now);

        ComplaintResolutionInfo resolution = ensureComplaintResolution(c);
        resolution.setReopenedAt(now);
        resolution.setReopenedBy(actor);
        resolution.setValidationStatus(ResolutionValidationStatus.REOPENED);

        caseTimelineService.append(
                c,
                CaseTimelineEventType.CASE_REOPENED,
                "Complaint reopened",
                firstNonBlank(request.comment(), "Complaint was reopened for another investigation cycle"),
                actor,
                previousStatus,
                c.getStatus(),
                Map.of()
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return sanitizeCaseForCurrentViewer(saved);
    }

    private void ensureStatusIn(CaseEntity caseEntity, Set<CaseStatus> allowedStatuses, String message) {
        if (caseEntity == null || allowedStatuses == null || allowedStatuses.contains(caseEntity.getStatus())) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Set<CaseStatus> assignSttAllowedStatuses(CaseEntity caseEntity) {
        return isComplaint(caseEntity) ? COMPLAINT_ASSIGN_STT_ALLOWED_STATUSES : ASSIGN_STT_ALLOWED_STATUSES;
    }

    private Set<CaseStatus> assignTechAllowedStatuses(CaseEntity caseEntity) {
        return isComplaint(caseEntity) ? COMPLAINT_ASSIGN_TECH_ALLOWED_STATUSES : ASSIGN_TECH_ALLOWED_STATUSES;
    }

    private Set<CaseStatus> selfExecutionAllowedStatuses(CaseEntity caseEntity) {
        return isComplaint(caseEntity) ? COMPLAINT_SELF_EXECUTION_ALLOWED_STATUSES : SELF_EXECUTION_ALLOWED_STATUSES;
    }

    private Set<CaseStatus> setRdvAllowedStatuses(CaseEntity caseEntity) {
        return isComplaint(caseEntity) ? COMPLAINT_SET_RDV_ALLOWED_STATUSES : SET_RDV_ALLOWED_STATUSES;
    }

    private Set<CaseStatus> completeWorkAllowedStatuses(CaseEntity caseEntity) {
        return isComplaint(caseEntity) ? COMPLAINT_COMPLETE_WORK_ALLOWED_STATUSES : COMPLETE_WORK_ALLOWED_STATUSES;
    }

    private Set<CaseStatus> workProofAllowedStatuses(CaseEntity caseEntity) {
        return isComplaint(caseEntity) ? COMPLAINT_WORK_PROOF_ALLOWED_STATUSES : WORK_PROOF_ALLOWED_STATUSES;
    }

    private Set<CaseStatus> validateByZmAllowedStatuses(CaseEntity caseEntity) {
        return isComplaint(caseEntity)
                ? EnumSet.of(CaseStatus.RESOLUTION_SUBMITTED)
                : EnumSet.of(CaseStatus.WORK_DONE);
    }

    private boolean isComplaint(CaseEntity caseEntity) {
        return caseEntity != null && caseEntity.getType() == CaseType.PLAINTE;
    }

    /** PLT Excel imports skip the mandatory RDV + diagnosis step and follow the ACT-like direct-TECH workflow. */
    private boolean isPltImportSource(CaseEntity caseEntity) {
        return caseEntity != null && "PLT_TODO_IMPORT".equals(caseEntity.getSource());
    }

    private void ensureComplaintCase(CaseEntity caseEntity) {
        if (!isComplaint(caseEntity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This endpoint is only available for complaints");
        }
    }

    private boolean hasCompletedComplaintRdv(CaseEntity caseEntity) {
        return caseEntity != null
                && caseEntity.getRdv() != null
                && Boolean.TRUE.equals(caseEntity.getRdv().getJoignable())
                && !Boolean.TRUE.equals(caseEntity.getRdv().getAbandon())
                && caseEntity.getRdv().getDatePriseRdv() != null;
    }

    private void ensureComplaintRequiresFieldExecution(CaseEntity caseEntity, String message) {
        if (!hasComplaintDiagnosisRequiringFieldExecution(caseEntity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean hasComplaintDiagnosisRequiringFieldExecution(CaseEntity caseEntity) {
        if (!isComplaint(caseEntity) || caseEntity.getDiagnosis() == null) {
            return false;
        }

        ComplaintDiagnosisInfo diagnosis = caseEntity.getDiagnosis();
        return Boolean.TRUE.equals(diagnosis.getRequiresFieldWork())
                || Boolean.TRUE.equals(diagnosis.getRequiresTechIntervention());
    }

    private boolean isComplaintDiagnosisDrivenExecution(CaseEntity caseEntity, CaseStatus previousStatus) {
        return isComplaint(caseEntity)
                && previousStatus != null
                && (previousStatus == CaseStatus.DIAGNOSIS_STEP
                || previousStatus == CaseStatus.RETURNED_TO_STT
                || previousStatus == CaseStatus.REOPENED);
    }

    private ComplaintResolutionInfo ensureComplaintResolution(CaseEntity caseEntity) {
        if (caseEntity.getResolution() == null) {
            caseEntity.setResolution(ComplaintResolutionInfo.builder().build());
        }
        return caseEntity.getResolution();
    }

    private String rdvTimelineDetails(SetRdvRequest request) {
        if (request.abandon()) {
            return "RDV marked as abandoned";
        }

        if (!request.joignable()) {
            return "Customer marked as not reachable";
        }

        return "RDV was successfully set";
    }

    private String caseTypeLabel(CaseType type) {
        if (type == CaseType.PLAINTE) {
            return "Complaint case";
        }

        if (type == CaseType.RESILIATION) {
            return "Resiliation case";
        }

        return "Activation case";
    }

    private String firstNonBlank(String primary, String fallback) {
        String safePrimary = trimToNull(primary);
        return safePrimary != null ? safePrimary : safe(fallback, null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String name, String email) {
        String safeName = name == null ? "" : name.trim();
        String safeEmail = email == null ? "" : email.trim();

        if (!safeName.isEmpty() && !safeEmail.isEmpty()) {
            return safeName + " (" + safeEmail + ")";
        }

        if (!safeName.isEmpty()) {
            return safeName;
        }

        if (!safeEmail.isEmpty()) {
            return safeEmail;
        }

        return "-";
    }

    private Map<String, Object> timelineData(Object... keyValuePairs) {
        Map<String, Object> data = new LinkedHashMap<>();

        if (keyValuePairs == null || keyValuePairs.length == 0) {
            return data;
        }

        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            Object rawKey = keyValuePairs[i];
            Object value = keyValuePairs[i + 1];

            if (!(rawKey instanceof String key) || key.isBlank() || value == null) {
                continue;
            }

            data.put(key, value);
        }

        return data;
    }

    private CaseEntity getCaseOrThrow(String caseId) {
        return repository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private void ensureCurrentUserHasRole(String role) {
        if (!currentUserHasRole(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void ensureCurrentUserCanActAsAssignedZm(CaseEntity c) {
        if (currentUserHasRole("ROLE_ADMIN")) {
            return;
        }

        if (!currentUserHasRole("ROLE_ZM")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned ZM can perform this action");
        }

        if (isBlank(c.getZmEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No ZM assigned to this case");
        }

        if (!equalsIgnoreCase(c.getZmEmail(), getCurrentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the assigned ZM for this case");
        }
    }

    private void ensureCurrentUserCanActAsAssignedStt(CaseEntity c) {
        if (currentUserHasRole("ROLE_ADMIN")) {
            return;
        }

        if (!currentUserHasRole("ROLE_STT")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned STT can perform this action");
        }

        if (isBlank(c.getSttEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No STT assigned to this case");
        }

        if (!equalsIgnoreCase(c.getSttEmail(), getCurrentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the assigned STT for this case");
        }
    }

    private void ensureCurrentUserCanActAsAssignedTech(CaseEntity c) {
        if (currentUserHasRole("ROLE_ADMIN")) {
            return;
        }

        if (!currentUserHasRole("ROLE_TECH")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned TECH can perform this action");
        }

        if (isBlank(c.getTechEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No TECH assigned to this case");
        }

        if (!equalsIgnoreCase(c.getTechEmail(), getCurrentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the assigned TECH for this case");
        }
    }

    private void ensureCurrentUserCanExecuteCase(CaseEntity c) {
        if (currentUserHasRole("ROLE_ADMIN")) {
            return;
        }

        if (currentUserHasRole("ROLE_STT")) {
            ensureCurrentUserCanActAsAssignedStt(c);

            if (!isSttSelfExecution(c)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "This case is not currently in STT self-execution mode"
                );
            }
            return;
        }

        if (currentUserHasRole("ROLE_TECH")) {
            ensureCurrentUserCanActAsAssignedTech(c);

            if (!isTechExecution(c)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "This case is not assigned to you as the active executor"
                );
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private boolean isSttSelfExecution(CaseEntity c) {
        return c != null
                && c.getExecutionMode() == ExecutionMode.STT_SELF_EXECUTION
                && c.getExecutorRole() == ExecutorRole.STT
                && equalsSafe(c.getExecutorId(), c.getSttId());
    }

    private boolean isTechExecution(CaseEntity c) {
        if (c == null) {
            return false;
        }
        // Imported cases may only carry techEmail (no techId) — accept either as a tech assignment.
        boolean hasTechAssignment = !isBlank(c.getTechId()) || !isBlank(c.getTechEmail());
        if (!hasTechAssignment) {
            return false;
        }

        // Simple assignment: no explicit execution-mode fields were stored (e.g. imported cases).
        if (c.getExecutionMode() == null && c.getExecutorRole() == null && isBlank(c.getExecutorId())) {
            return true;
        }

        // Formal TECH_EXECUTION: executor must match the tech assignment.
        // When techId is absent, any TECH_EXECUTION assignment is accepted (email match was done at assignment time).
        boolean executorMatchesTech = isBlank(c.getTechId())
                || equalsSafe(c.getExecutorId(), c.getTechId());
        return c.getExecutionMode() == ExecutionMode.TECH_EXECUTION
                && c.getExecutorRole() == ExecutorRole.TECH
                && executorMatchesTech;
    }

    private boolean currentUserHasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        return authentication.getName();
    }

    private void ensureUserHasRole(AppUser user, UserRole expectedRole, String message) {
        if (user.getRole() == null || user.getRole() != expectedRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void ensureUserIsActive(AppUser user, String message) {
        if (user.getStatus() != AccountStatus.APPROVED || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private AppUser resolveAssignedTechnician(CaseEntity caseEntity) {
        AppUser technician;
        if (!isBlank(caseEntity.getTechId())) {
            technician = userRepository.findById(caseEntity.getTechId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned technician not found"));
        } else if (!isBlank(caseEntity.getTechEmail())) {
            technician = userRepository.findByEmail(caseEntity.getTechEmail())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned technician not found"));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No technician assigned to this case");
        }

        ensureUserHasRole(technician, UserRole.ROLE_TECH, "Assigned executor is not a technician");
        return technician;
    }

    private AppUser resolveAssignedStt(CaseEntity caseEntity) {
        if (!isBlank(caseEntity.getSttId())) {
            return userRepository.findById(caseEntity.getSttId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assigned STT not found"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No STT assigned to this case");
    }

    private void clearSttAssignment(CaseEntity c) {
        c.setAssignedSttId(null);
        c.setSttId(null);
        c.setSttEmail(null);
        c.setSttName(null);
        c.setDateAffectationStt(null);
    }

    private void clearTechAssignment(CaseEntity c) {
        c.setTechId(null);
        c.setTechEmail(null);
        c.setTechName(null);
        c.setDateAffectationTech(null);
    }

    private void clearExecutionDecision(CaseEntity c) {
        c.setExecutorId(null);
        c.setExecutorRole(null);
        c.setExecutionMode(null);
    }

    private void resetExecutionState(CaseEntity c) {
        clearTechAssignment(c);
        clearExecutionDecision(c);
        c.setSlaTracking(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean equalsSafe(String a, String b) {
        return a != null && a.equals(b);
    }

    public AppUser resolveOwnerZmForGovernorate(String governorate) {
        return resolveOwnerZm(governorate, CaseType.PLAINTE);
    }

    public String normalizeGovernorateValue(String governorate) {
        return normalizeGovernorate(governorate);
    }

    public boolean sttCoversGovernorateValue(AppUser stt, String governorate) {
        return sttCoversGovernorate(stt, governorate);
    }

    private AppUser resolveOwnerZm(String governorate, CaseType caseType) {
        String normalizedGovernorate = normalizeGovernorate(governorate);
        String moduleKey = CaseModuleType.fromCaseType(caseType).name();

        List<AppUser> matches = userRepository.findByRoleOrderByFullNameAsc(UserRole.ROLE_ZM).stream()
                .filter(user -> user.getRole() == UserRole.ROLE_ZM)
                .filter(user -> user.getStatus() == AccountStatus.APPROVED)
                .filter(AppUser::isEnabled)
                .filter(user -> zmCoversGovernorateForModule(user, normalizedGovernorate, moduleKey))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No ZM covers governorate " + normalizedGovernorate
            );
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governorate " + normalizedGovernorate + " is assigned to multiple ZMs"
            );
        }

        return matches.get(0);
    }

    private boolean sttCoversGovernorate(AppUser stt, String governorate) {
        if (stt == null || isBlank(governorate)) {
            return false;
        }

        return TunisiaGovernorateSupport.containsEquivalent(stt.getCoveredGovernorates(), governorate);
    }

    private boolean zmCoversGovernorateForModule(AppUser zm, String normalizedGovernorate, String moduleKey) {
        Map<String, List<String>> coverage = zm.getCoverageByModule();
        if (coverage != null && coverage.containsKey(moduleKey)) {
            List<String> moduleGovs = coverage.get(moduleKey);
            if (moduleGovs != null && !moduleGovs.isEmpty()) {
                return TunisiaGovernorateSupport.containsEquivalent(moduleGovs, normalizedGovernorate);
            }
        }
        // Backward compat: for ACT, fall back to legacy assignedGovernorates when module coverage is not yet set
        if ("ACT".equals(moduleKey)) {
            return TunisiaGovernorateSupport.containsEquivalent(zm.getAssignedGovernorates(), normalizedGovernorate);
        }
        return false;
    }

    private String normalizeGovernorate(String governorate) {
        if (isBlank(governorate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Governorate is required");
        }

        try {
            return TunisiaGovernorateSupport.requireSupported(governorate);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private void validateLocationSnapshot(Double latitude, Double longitude, String message) {
        if (latitude == null && longitude == null) {
            return;
        }

        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void enrichSlaReadValues(List<CaseEntity> cases) {
        Instant now = Instant.now();
        for (CaseEntity c : cases) {
            slaTrackingService.refreshLiveState(c, now);
        }
    }

    private void enrichWorkProofReadValues(List<CaseEntity> cases) {
        for (CaseEntity c : cases) {
            enrichWorkProofReadValues(c);
        }
    }

    private void enrichWorkProofReadValues(CaseEntity c) {
        if (c == null || isBlank(c.getId())) {
            return;
        }

        List<FileMetadata> proofs = fileStorageService.listByEntityAndRole(
                FileEntityType.CASE,
                c.getId(),
                FileRole.WORK_PROOF
        );

        List<CaseProofFileResponse> proofResponses = proofs.stream()
                .map(this::toProofResponse)
                .collect(Collectors.toList());

        c.setWorkProofs(proofResponses);
    }

    private CaseProofFileResponse toProofResponse(FileMetadata metadata) {
        return new CaseProofFileResponse(
                metadata.getId(),
                metadata.getOriginalFilename(),
                metadata.getContentType(),
                metadata.getSize(),
                metadata.getUploadedBy(),
                metadata.getCreatedAt(),
                fileStorageService.buildViewUrl(metadata.getId()),
                fileStorageService.buildDownloadUrl(metadata.getId()),
                metadata.getLocationLatitude(),
                metadata.getLocationLongitude(),
                metadata.getLocationCapturedAt(),
                metadata.getLocationAccuracyMeters(),
                metadata.getCaptureSource()
        );
    }

    private void sanitizeCaseListForCurrentViewer(List<CaseEntity> cases) {
        if (cases == null || cases.isEmpty()) {
            return;
        }

        for (CaseEntity caseEntity : cases) {
            sanitizeCaseForCurrentViewer(caseEntity);
        }
    }

    private CaseEntity sanitizeCaseForCurrentViewer(CaseEntity caseEntity) {
        if (caseEntity == null || canViewCapturedGps(caseEntity)) {
            return caseEntity;
        }

        redactCapturedGps(caseEntity);
        return caseEntity;
    }

    private boolean canViewCapturedGps(CaseEntity caseEntity) {
        if (caseEntity == null) {
            return false;
        }

        if (currentUserHasRole("ROLE_ADMIN")) {
            return true;
        }

        return currentUserHasRole("ROLE_ZM") && equalsIgnoreCase(caseEntity.getZmEmail(), getCurrentUsername());
    }

    private void redactCapturedGps(CaseEntity caseEntity) {
        if (caseEntity == null) {
            return;
        }

        if (caseEntity.getWork() != null) {
            caseEntity.getWork().setLocationLatitude(null);
            caseEntity.getWork().setLocationLongitude(null);
            caseEntity.getWork().setLocationCapturedAt(null);
            caseEntity.getWork().setLocationAccuracyMeters(null);
        }

        if (caseEntity.getTimeline() != null) {
            caseEntity.getTimeline().forEach(this::redactTimelineGpsData);
        }

        if (caseEntity.getWorkProofs() != null && !caseEntity.getWorkProofs().isEmpty()) {
            List<CaseProofFileResponse> sanitizedProofs = new ArrayList<>();

            for (CaseProofFileResponse proof : caseEntity.getWorkProofs()) {
                sanitizedProofs.add(new CaseProofFileResponse(
                        proof.fileId(),
                        proof.originalFilename(),
                        proof.contentType(),
                        proof.size(),
                        proof.uploadedBy(),
                        proof.createdAt(),
                        proof.viewUrl(),
                        proof.downloadUrl(),
                        null,
                        null,
                        null,
                        null,
                        proof.captureSource()
                ));
            }

            caseEntity.setWorkProofs(List.copyOf(sanitizedProofs));
        }
    }

    private void redactTimelineGpsData(CaseTimelineEvent event) {
        if (event == null || event.getData() == null || event.getData().isEmpty()) {
            return;
        }

        Map<String, Object> sanitized = new LinkedHashMap<>(event.getData());
        sanitized.remove("locationLatitude");
        sanitized.remove("locationLongitude");
        sanitized.remove("locationCapturedAt");
        sanitized.remove("locationAccuracyMeters");
        event.setData(sanitized);
    }

    private List<String> safeGovernorates(List<String> governorates) {
        return TunisiaGovernorateSupport.normalizeStoredValues(governorates);
    }
}
