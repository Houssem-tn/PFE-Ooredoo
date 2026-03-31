package com.pfe.case_management.service;

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
import com.pfe.case_management.dto.AssignZmRequest;
import com.pfe.case_management.dto.CaseProofFileResponse;
import com.pfe.case_management.dto.CompleteWorkRequest;
import com.pfe.case_management.dto.CreateCaseRequest;
import com.pfe.case_management.dto.SetRdvRequest;
import com.pfe.case_management.dto.ValidateZmRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.RdvInfo;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseService {

    private static final Set<CaseStatus> ASSIGN_ZM_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.CREATED,
        CaseStatus.ASSIGNED_TO_ZM,
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.NOT_REACHABLE,
        CaseStatus.RDV_SET,
        CaseStatus.WORK_DONE,
        CaseStatus.REJECTED_BY_ZM,
        CaseStatus.ABANDONED
    );

    private static final Set<CaseStatus> ASSIGN_STT_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_ZM,
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> ASSIGN_TECH_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_STT,
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.NOT_REACHABLE,
        CaseStatus.REJECTED_BY_ZM
    );

    private static final Set<CaseStatus> SET_RDV_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET,
        CaseStatus.NOT_REACHABLE
    );

    private static final Set<CaseStatus> COMPLETE_WORK_ALLOWED_STATUSES = EnumSet.of(
        CaseStatus.ASSIGNED_TO_TECH,
        CaseStatus.RDV_SET
    );

    private static final Set<CaseStatus> WORK_PROOF_ALLOWED_STATUSES = EnumSet.of(
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

    public CaseEntity createCase(CreateCaseRequest request) {
        ensureCurrentUserHasRole("ROLE_ADMIN");

        if (repository.findByCrmCase(request.crmCase()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CRM case already exists");
        }

        Instant now = Instant.now();

        CaseEntity entity = CaseEntity.builder()
                .type(request.type())
                .crmCase(request.crmCase())
                .dateCreationCrm(request.dateCreationCrm())
                .openingDateTimos(request.openingDateTimos())
                .status(CaseStatus.CREATED)
                .clientName(request.clientName())
                .clientPhone(request.clientPhone())
                .msisdn(request.msisdn())
                .commentaire(request.commentaire())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .localite(request.localite())
                .delegation(request.delegation())
                .gouvernorat(request.gouvernorat())
                .siteSecteur(request.siteSecteur())
                .offre(request.offre())
                .desPack(request.desPack())
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

        return repository.save(entity);
    }

    public List<CaseEntity> getAllCasesByType(CaseType type) {
        List<CaseEntity> scoped;

        if (currentUserHasRole("ROLE_ADMIN")) {
            scoped = repository.findByType(type);
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            return scoped;
        }

        if (currentUserHasRole("ROLE_ZM")) {
            scoped = repository.findByTypeAndZmEmailIgnoreCase(type, getCurrentUsername());
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            return scoped;
        }

        if (currentUserHasRole("ROLE_STT")) {
            scoped = repository.findByTypeAndSttEmailIgnoreCase(type, getCurrentUsername());
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            return scoped;
        }

        if (currentUserHasRole("ROLE_TECH")) {
            scoped = repository.findByTypeAndTechEmailIgnoreCase(type, getCurrentUsername());
            enrichSlaReadValues(scoped);
            enrichWorkProofReadValues(scoped);
            return scoped;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    public CaseEntity getCaseById(String caseId) {
        CaseEntity c = getCaseOrThrow(caseId);

        if (currentUserHasRole("ROLE_ADMIN")) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return c;
        }

        String current = getCurrentUsername();

        if (currentUserHasRole("ROLE_ZM") && equalsIgnoreCase(c.getZmEmail(), current)) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return c;
        }

        if (currentUserHasRole("ROLE_STT") && equalsIgnoreCase(c.getSttEmail(), current)) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return c;
        }

        if (currentUserHasRole("ROLE_TECH") && equalsIgnoreCase(c.getTechEmail(), current)) {
            slaTrackingService.refreshLiveState(c, Instant.now());
            enrichWorkProofReadValues(c);
            return c;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this case");
    }

    public CaseEntity assignToZm(String caseId, AssignZmRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        ensureCurrentUserHasRole("ROLE_ADMIN");
        String triggeredBy = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        ensureStatusIn(
            c,
            ASSIGN_ZM_ALLOWED_STATUSES,
            "Case cannot be assigned to ZM in current state"
        );

        AppUser zm = userRepository.findById(request.zmId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ZM not found"));

        ensureUserHasRole(zm, UserRole.ROLE_ZM, "Selected user is not a ZM");
        ensureUserIsActive(zm, "Selected ZM is not active");

        boolean zmChanged = !equalsSafe(c.getZmId(), zm.getId());

        c.setZmId(zm.getId());
        c.setZmEmail(zm.getEmail());
        c.setZmName(zm.getFullName());
        c.setDateAffectationZm(Instant.now());

        if (zmChanged) {
            clearSttAssignment(c);
            clearTechAssignment(c);
        }

        c.setStatus(CaseStatus.ASSIGNED_TO_ZM);
        c.setUpdatedAt(Instant.now());

        caseTimelineService.append(
            c,
            CaseTimelineEventType.ASSIGNMENT_UPDATED,
            "ZM assignment updated",
            "Case assigned to ZM " + safe(c.getZmName(), c.getZmEmail()),
            triggeredBy,
            previousStatus,
            c.getStatus(),
            timelineData(
                "assigneeRole", "ROLE_ZM",
                "assigneeEmail", c.getZmEmail(),
                "resetDownstreamAssignments", zmChanged
            )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        notificationEventService.onCaseAssignedToZm(saved, triggeredBy);
        return saved;
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

        if (isBlank(c.getZmId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case must be assigned to a ZM first");
        }

        AppUser stt = userRepository.findById(request.sttId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "STT not found"));

        ensureUserHasRole(stt, UserRole.ROLE_STT, "Selected user is not an STT");
        ensureUserIsActive(stt, "Selected STT is not active");

        if (!equalsSafe(stt.getAssignedZmId(), c.getZmId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This STT does not belong to the assigned ZM"
            );
        }

        boolean sttChanged = !equalsSafe(c.getSttId(), stt.getId());

        c.setSttId(stt.getId());
        c.setSttEmail(stt.getEmail());
        c.setSttName(stt.getFullName());
        c.setDateAffectationStt(Instant.now());

        if (sttChanged) {
            clearTechAssignment(c);
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
        return saved;
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
        c.setDateAffectationTech(Instant.now());

        c.setStatus(CaseStatus.ASSIGNED_TO_TECH);
        c.setUpdatedAt(Instant.now());
        slaTrackingService.startRdvSla(c, c.getDateAffectationTech());

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
                "assigneeEmail", c.getTechEmail()
            )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        notificationEventService.onCaseAssignedToTech(saved, triggeredBy);
        return saved;
    }

    public CaseEntity setRdv(String caseId, SetRdvRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        if (currentUserHasRole("ROLE_STT")) {
            ensureCurrentUserCanActAsAssignedStt(c);
        } else if (currentUserHasRole("ROLE_TECH")) {
            ensureCurrentUserCanActAsAssignedTech(c);
        } else if (!currentUserHasRole("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

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
        enrichWorkProofReadValues(saved);

        if (triggerImmediateBreachNotification) {
            notificationEventService.onSlaBreached(saved);
        }

        return saved;
    }

    public CaseEntity completeWork(String caseId, CompleteWorkRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();
        CaseStatus previousStatus = c.getStatus();

        if (currentUserHasRole("ROLE_TECH")) {
            ensureCurrentUserCanActAsAssignedTech(c);
        } else if (currentUserHasRole("ROLE_STT")) {
            ensureCurrentUserCanActAsAssignedStt(c);
        } else if (!currentUserHasRole("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        ensureStatusIn(c, COMPLETE_WORK_ALLOWED_STATUSES, "Work completion is not allowed in current state");

        WorkInfo work = new WorkInfo();
        work.setRepTravauxStt(request.repTravauxStt());
        work.setDateFinTrv(request.dateFinTrv());
        work.setCmtTrvStt(request.cmtTrvStt());
        work.setUpdatedAt(Instant.now());
        work.setUpdatedBy(getCurrentUsername());

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
                "debitUl", request.debitUl()
            )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return saved;
    }

    public CaseEntity uploadWorkProof(String caseId, MultipartFile file) {
        CaseEntity c = getCaseOrThrow(caseId);
        String actor = getCurrentUsername();

        if (currentUserHasRole("ROLE_TECH")) {
            ensureCurrentUserCanActAsAssignedTech(c);
        } else if (currentUserHasRole("ROLE_STT")) {
            ensureCurrentUserCanActAsAssignedStt(c);
        } else if (!currentUserHasRole("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        ensureStatusIn(c, WORK_PROOF_ALLOWED_STATUSES, "Work proof upload is not allowed in current state");

        FileMetadata metadata = fileStorageService.upload(
                file,
                FileEntityType.CASE,
                c.getId(),
                FileRole.WORK_PROOF,
                actor
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
                        "proofFilename", metadata.getOriginalFilename()
                )
        );

        CaseEntity saved = repository.save(c);
        enrichWorkProofReadValues(saved);
        return saved;
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
        return saved;
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
        return saved;
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
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void clearSttAssignment(CaseEntity c) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean equalsSafe(String a, String b) {
        return a != null && a.equals(b);
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
                fileStorageService.buildDownloadUrl(metadata.getId())
        );
    }
}
