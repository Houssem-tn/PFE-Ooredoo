package com.pfe.case_management.service;

import com.pfe.Entity.AccountStatus;
import com.pfe.Entity.AppUser;
import com.pfe.Entity.FileEntityType;
import com.pfe.Entity.FileMetadata;
import com.pfe.Entity.FileRole;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.Service.FileStorageService;
import com.pfe.Service.NotificationEventService;
import com.pfe.case_management.dto.AssignSttRequest;
import com.pfe.case_management.dto.AssignTechRequest;
import com.pfe.case_management.dto.CaseProofFileResponse;
import com.pfe.case_management.dto.CompleteWorkRequest;
import com.pfe.case_management.dto.CreateCaseRequest;
import com.pfe.case_management.dto.SetRdvRequest;
import com.pfe.case_management.dto.ValidateZmRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ExecutionMode;
import com.pfe.case_management.enums.ExecutorRole;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.RdvInfo;
import com.pfe.case_management.model.CaseTimelineEvent;
import com.pfe.case_management.model.TechnicalMetrics;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.model.ZmValidation;
import com.pfe.case_management.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
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
    private static final List<String> TUNISIA_GOVERNORATES = List.of(
            "Tunis",
            "Ariana",
            "Ben Arous",
            "Mannouba",
            "Nabeul",
            "Zaghouan",
            "Bizerte",
            "B\u00E9ja",
            "Jendouba",
            "Le Kef",
            "Siliana",
            "Sousse",
            "Monastir",
            "Mahdia",
            "Sfax",
            "Kairouan",
            "Kasserine",
            "Sidi Bouzid",
            "Gab\u00E8s",
            "M\u00E9denine",
            "Tataouine",
            "Gafsa",
            "Tozeur",
            "K\u00E9bili"
    );
    private static final Set<String> TUNISIA_GOVERNORATE_SET = Set.copyOf(TUNISIA_GOVERNORATES);

    private static final Set<CaseStatus> ASSIGN_STT_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_ZM,
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> ASSIGN_TECH_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.NOT_REACHABLE,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> SELF_EXECUTION_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.NOT_REACHABLE,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> SET_RDV_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.NOT_REACHABLE
    );

    private static final Set<CaseStatus> COMPLETE_WORK_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET
    );

    private static final Set<CaseStatus> WORK_PROOF_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.WORK_DONE,
        CaseStatus.REJECTED_BY_ZM,
        CaseStatus.VALIDATED_BY_ZM
    );

    private final CaseRepository repository;
    private final UserRepository userRepository;
    private final SlaTrackingService slaTrackingService;
    private final NotificationEventService notificationEventService;
    private final CaseTimelineService caseTimelineService;
    private final FileStorageService fileStorageService;
    private final CasePdfExportService casePdfExportService;

    public CaseEntity createCase(CreateCaseRequest request) {
        ensureCurrentUserHasRole("ROLE_ADMIN");

        if (repository.findByCrmCase(request.crmCase()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CRM case already exists");
        }

        Instant now = Instant.now();
        String governorate = normalizeGovernorate(request.gouvernorat());
        AppUser ownerZm = resolveOwnerZm(governorate);

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
                "New activation case captured from CRM",
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
            ASSIGN_STT_ALLOWED_STATUSES,
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
            ASSIGN_TECH_ALLOWED_STATUSES,
            "Case cannot be assigned to TECH in current state"
        );

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
        if (!preserveProgressState) {
            c.setStatus(CaseStatus.ASSIGNED_TO_TECH);
            slaTrackingService.startRdvSla(c, assignmentTime);
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
                SELF_EXECUTION_ALLOWED_STATUSES,
                "Case cannot switch to STT self-execution in current state"
        );

        if (isBlank(c.getSttId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case must be assigned to an STT first");
        }

        Instant switchTime = Instant.now();
        clearTechAssignment(c);
        c.setExecutorId(c.getSttId());
        c.setExecutorRole(ExecutorRole.STT);
        c.setExecutionMode(ExecutionMode.STT_SELF_EXECUTION);

        boolean preserveProgressState = previousStatus == CaseStatus.RDV_SET || previousStatus == CaseStatus.NOT_REACHABLE;
        if (!preserveProgressState) {
            c.setStatus(CaseStatus.ASSIGNED_TO_STT);
            slaTrackingService.startRdvSla(c, switchTime);
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

        ensureStatusIn(c, SET_RDV_ALLOWED_STATUSES, "RDV update is not allowed in current state");

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
            c.setStatus(CaseStatus.RDV_SET);
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

    public CaseEntity completeWork(String caseId, CompleteWorkRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanExecuteCase(c);

        ensureStatusIn(c, COMPLETE_WORK_ALLOWED_STATUSES, "Work completion is not allowed in current state");
        validateLocationSnapshot(
                request.locationLatitude(),
                request.locationLongitude(),
                "Work submission location is invalid"
        );

        WorkInfo work = new WorkInfo();
        work.setRepTravauxStt(request.repTravauxStt());
        work.setDateFinTrv(request.dateFinTrv());
        work.setCmtTrvStt(request.cmtTrvStt());
        work.setUpdatedAt(Instant.now());
        work.setUpdatedBy(getCurrentUsername());
        work.setLocationLatitude(request.locationLatitude());
        work.setLocationLongitude(request.locationLongitude());
        work.setLocationCapturedAt(request.locationCapturedAt());
        work.setLocationAccuracyMeters(request.locationAccuracyMeters());

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
        c.setStatus(CaseStatus.WORK_DONE);
        c.setUpdatedAt(Instant.now());

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
                "locationAccuracyMeters", request.locationAccuracyMeters()
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

        ensureStatusIn(c, WORK_PROOF_ALLOWED_STATUSES, "Work proof upload is not allowed in current state");
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

    public CaseEntity validateByZm(String caseId, ValidateZmRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureCurrentUserCanActAsAssignedZm(c);
        ensureStatusIn(c, EnumSet.of(CaseStatus.WORK_DONE), "ZM validation is only allowed after work completion");

        ZmValidation validation = new ZmValidation();
        validation.setApproved(Boolean.TRUE.equals(request.approved()));
        validation.setCmtZm(request.comment());
        validation.setValidatedAt(Instant.now());
        validation.setValidatedBy(getCurrentUsername());

        c.setZmValidation(validation);

        if (Boolean.TRUE.equals(request.approved())) {
            c.setStatus(CaseStatus.VALIDATED_BY_ZM);
        } else {
            c.setStatus(CaseStatus.REJECTED_BY_ZM);
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

    private void ensureStatusIn(CaseEntity caseEntity, Set<CaseStatus> allowedStatuses, String message) {
        if (caseEntity == null || allowedStatuses == null || allowedStatuses.contains(caseEntity.getStatus())) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
        if (c == null || isBlank(c.getTechId())) {
            return false;
        }

        if (c.getExecutionMode() == null && c.getExecutorRole() == null && isBlank(c.getExecutorId())) {
            return true;
        }

        return c.getExecutionMode() == ExecutionMode.TECH_EXECUTION
                && c.getExecutorRole() == ExecutorRole.TECH
                && equalsSafe(c.getExecutorId(), c.getTechId());
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

    private AppUser resolveOwnerZm(String governorate) {
        List<AppUser> matches = userRepository.findByRoleAndAssignedGovernoratesIn(
                        UserRole.ROLE_ZM,
                        List.of(governorate)
                ).stream()
                .filter(user -> user.getRole() == UserRole.ROLE_ZM)
                .filter(user -> user.getStatus() == AccountStatus.APPROVED)
                .filter(AppUser::isEnabled)
                .filter(user -> safeGovernorates(user.getAssignedGovernorates()).contains(governorate))
                .toList();

        if (matches.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No ZM covers governorate " + governorate
            );
        }

        if (matches.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governorate " + governorate + " is assigned to multiple ZMs"
            );
        }

        return matches.get(0);
    }

    private boolean sttCoversGovernorate(AppUser stt, String governorate) {
        if (stt == null || isBlank(governorate)) {
            return false;
        }

        return safeGovernorates(stt.getCoveredGovernorates()).contains(governorate.trim());
    }

    private String normalizeGovernorate(String governorate) {
        if (isBlank(governorate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Governorate is required");
        }

        String normalized = governorate.trim();
        if (!TUNISIA_GOVERNORATE_SET.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported governorate: " + normalized);
        }

        return normalized;
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
        if (governorates == null || governorates.isEmpty()) {
            return List.of();
        }

        return governorates.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }
}
