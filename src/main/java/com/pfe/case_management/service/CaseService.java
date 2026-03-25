package com.pfe.case_management.service;

import com.pfe.Entity.AppUser;
import com.pfe.Entity.UserRole;
import com.pfe.Repository.UserRepository;
import com.pfe.case_management.dto.AssignSttRequest;
import com.pfe.case_management.dto.AssignTechRequest;
import com.pfe.case_management.dto.AssignZmRequest;
import com.pfe.case_management.dto.CompleteWorkRequest;
import com.pfe.case_management.dto.CreateCaseRequest;
import com.pfe.case_management.dto.SetRdvRequest;
import com.pfe.case_management.dto.ValidateZmRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final CaseRepository repository;
    private final UserRepository userRepository;

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

        return repository.save(entity);
    }

    public List<CaseEntity> getAllCasesByType(CaseType type) {
        if (currentUserHasRole("ROLE_ADMIN")) {
            return repository.findByType(type);
        }

        if (currentUserHasRole("ROLE_ZM")) {
            return repository.findByTypeAndZmEmailIgnoreCase(type, getCurrentUsername());
        }

        if (currentUserHasRole("ROLE_STT")) {
            return repository.findByTypeAndSttEmailIgnoreCase(type, getCurrentUsername());
        }

        if (currentUserHasRole("ROLE_TECH")) {
            return repository.findByTypeAndTechEmailIgnoreCase(type, getCurrentUsername());
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    public CaseEntity getCaseById(String caseId) {
        CaseEntity c = getCaseOrThrow(caseId);

        if (currentUserHasRole("ROLE_ADMIN")) {
            return c;
        }

        String current = getCurrentUsername();

        if (currentUserHasRole("ROLE_ZM") && equalsIgnoreCase(c.getZmEmail(), current)) {
            return c;
        }

        if (currentUserHasRole("ROLE_STT") && equalsIgnoreCase(c.getSttEmail(), current)) {
            return c;
        }

        if (currentUserHasRole("ROLE_TECH") && equalsIgnoreCase(c.getTechEmail(), current)) {
            return c;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this case");
    }

    public CaseEntity assignToZm(String caseId, AssignZmRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);
        ensureCurrentUserHasRole("ROLE_ADMIN");

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

        return repository.save(c);
    }

    public CaseEntity assignToStt(String caseId, AssignSttRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);

        ensureCurrentUserCanActAsAssignedZm(c);

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

        return repository.save(c);
    }

    public CaseEntity assignToTech(String caseId, AssignTechRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);

        ensureCurrentUserCanActAsAssignedStt(c);

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

        return repository.save(c);
    }

    public CaseEntity setRdv(String caseId, SetRdvRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);

        if (currentUserHasRole("ROLE_STT")) {
            ensureCurrentUserCanActAsAssignedStt(c);
        } else if (currentUserHasRole("ROLE_TECH")) {
            ensureCurrentUserCanActAsAssignedTech(c);
        } else if (!currentUserHasRole("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        RdvInfo rdv = new RdvInfo();
        rdv.setJoignable(request.joignable());
        rdv.setDatePriseRdv(request.datePriseRdv());
        rdv.setCauseInjoignabilite(request.causeInjoignabilite());
        rdv.setDetail(request.detail());
        rdv.setCmtRdv(request.cmtRdv());
        rdv.setAbandon(request.abandon());
        rdv.setCauseAbandon(request.causeAbandon());

        c.setRdv(rdv);

        if (!request.joignable()) {
            c.setStatus(CaseStatus.NOT_REACHABLE);
        } else if (request.abandon()) {
            c.setStatus(CaseStatus.ABANDONED);
        } else {
            c.setStatus(CaseStatus.RDV_SET);
        }

        c.setUpdatedAt(Instant.now());

        return repository.save(c);
    }

    public CaseEntity completeWork(String caseId, CompleteWorkRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);

        if (currentUserHasRole("ROLE_TECH")) {
            ensureCurrentUserCanActAsAssignedTech(c);
        } else if (currentUserHasRole("ROLE_STT")) {
            ensureCurrentUserCanActAsAssignedStt(c);
        } else if (!currentUserHasRole("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

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

        return repository.save(c);
    }

    public CaseEntity validateByZm(String caseId, ValidateZmRequest request) {
        CaseEntity c = getCaseOrThrow(caseId);

        ensureCurrentUserCanActAsAssignedZm(c);

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

        return repository.save(c);
    }

    public CaseEntity closeCase(String caseId) {
        CaseEntity c = getCaseOrThrow(caseId);

        ensureCurrentUserCanActAsAssignedZm(c);

        if (c.getStatus() != CaseStatus.VALIDATED_BY_ZM) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only a case validated by ZM can be closed"
            );
        }

        c.setStatus(CaseStatus.CLOSED);
        c.setUpdatedAt(Instant.now());

        return repository.save(c);
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
}