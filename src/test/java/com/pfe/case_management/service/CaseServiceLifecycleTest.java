package com.pfe.case_management.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.AssignSttRequest;
import com.pfe.case_management.dto.AssignTechRequest;
import com.pfe.case_management.dto.SetRdvRequest;
import com.pfe.case_management.dto.ValidateZmRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ExecutionMode;
import com.pfe.case_management.enums.ExecutorRole;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.retrieved_stock.RetrievedStockService;
import com.pfe.stock_management.service.EquipmentUsagePolicyService;
import com.pfe.stock_management.service.SttStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.pfe.support.SecurityTestSupport.authenticateAs;
import static com.pfe.support.SecurityTestSupport.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Focused tests for the case state machine: valid transitions, and the
 * ownership/role guards that make invalid transitions and cross-team access
 * fail with the correct HTTP status.
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceLifecycleTest {

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SlaTrackingService slaTrackingService;
    @Mock
    private NotificationEventService notificationEventService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private CasePdfExportService casePdfExportService;
    @Mock
    private SttStockService sttStockService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private RetrievedStockService retrievedStockService;
    @Mock
    private EquipmentUsagePolicyService equipmentUsagePolicy;
    @Mock
    private BusinessMetrics businessMetrics;

    @AfterEach
    void clearSecurityContext() {
        clear();
    }

    private CaseService newService() {
        return new CaseService(
                caseRepository,
                mongoTemplate,
                userRepository,
                slaTrackingService,
                notificationEventService,
                new CaseTimelineService(),
                fileStorageService,
                casePdfExportService,
                sttStockService,
                equipmentUsagePolicy,
                retrievedStockService,
                businessMetrics
        );
    }

    private void stubSave() {
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── assignToStt ──────────────────────────────────────────────────────────

    @Test
    void assignToStt_shouldAssignWhenCallerIsTheOwningZmAndSttCoversGovernorate() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();
        stubSave();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_ZM)
                .zmId("zm-1")
                .zmEmail("zm@example.com")
                .gouvernorat("Tunis")
                .build();

        AppUser stt = AppUser.builder()
                .id("stt-1")
                .email("stt@example.com")
                .fullName("Acme STT")
                .role(UserRole.ROLE_STT)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .coveredGovernorates(List.of("Tunis"))
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findById("stt-1")).thenReturn(Optional.of(stt));

        CaseEntity result = service.assignToStt("case-1", new AssignSttRequest("stt-1"));

        assertThat(result.getStatus()).isEqualTo(CaseStatus.ASSIGNED_TO_STT);
        assertThat(result.getSttId()).isEqualTo("stt-1");
        assertThat(result.getSttEmail()).isEqualTo("stt@example.com");
    }

    @Test
    void assignToStt_shouldRejectWhenCallerIsNotTheAssignedZm() {
        authenticateAs("someone-else@example.com", "ROLE_ZM");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_ZM)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.assignToStt("case-1", new AssignSttRequest("stt-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void assignToStt_shouldRejectInvalidStatusTransition() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();

        // CREATED is not in ASSIGN_STT_ALLOWED_STATUSES for an ACTIVATION case.
        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.CREATED)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.assignToStt("case-1", new AssignSttRequest("stt-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void assignToStt_shouldRejectWhenSttDoesNotCoverCaseGovernorate() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_ZM)
                .zmId("zm-1")
                .zmEmail("zm@example.com")
                .gouvernorat("Sfax")
                .build();

        AppUser stt = AppUser.builder()
                .id("stt-1")
                .email("stt@example.com")
                .role(UserRole.ROLE_STT)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .coveredGovernorates(List.of("Tunis"))
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findById("stt-1")).thenReturn(Optional.of(stt));

        assertThatThrownBy(() -> service.assignToStt("case-1", new AssignSttRequest("stt-1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not cover the case governorate");
    }

    // ── assignToTech ─────────────────────────────────────────────────────────

    @Test
    void assignToTech_shouldRejectWhenTechDoesNotBelongToAssignedStt() {
        authenticateAs("stt@example.com", "ROLE_STT");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_STT)
                .sttId("stt-1")
                .sttEmail("stt@example.com")
                .build();

        AppUser tech = AppUser.builder()
                .id("tech-1")
                .email("tech@example.com")
                .role(UserRole.ROLE_TECH)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .assignedSttId("stt-OTHER")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findById("tech-1")).thenReturn(Optional.of(tech));

        assertThatThrownBy(() -> service.assignToTech("case-1", new AssignTechRequest("tech-1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to the assigned STT");
    }

    @Test
    void assignToTech_shouldRejectWhenCallerIsNotTheAssignedStt() {
        authenticateAs("someone-else@example.com", "ROLE_STT");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_STT)
                .sttId("stt-1")
                .sttEmail("stt@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.assignToTech("case-1", new AssignTechRequest("tech-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void assignToTech_shouldAssignAndStartSlaWhenValid() {
        authenticateAs("stt@example.com", "ROLE_STT");
        CaseService service = newService();
        stubSave();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_STT)
                .sttId("stt-1")
                .sttEmail("stt@example.com")
                .build();

        AppUser tech = AppUser.builder()
                .id("tech-1")
                .email("tech@example.com")
                .fullName("Tech One")
                .role(UserRole.ROLE_TECH)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .assignedSttId("stt-1")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findById("tech-1")).thenReturn(Optional.of(tech));

        CaseEntity result = service.assignToTech("case-1", new AssignTechRequest("tech-1"));

        assertThat(result.getStatus()).isEqualTo(CaseStatus.ASSIGNED_TO_TECH);
        assertThat(result.getTechId()).isEqualTo("tech-1");
        assertThat(result.getExecutorRole()).isEqualTo(ExecutorRole.TECH);
        org.mockito.Mockito.verify(slaTrackingService).startRdvSla(any(), any());
    }

    // ── setRdv: active-executor guard (not merely "assigned") ─────────────────

    @Test
    void setRdv_shouldRejectWhenCaseIsAssignedToTechButExecutionModeIsNotTech() {
        // techEmail matches the caller (ownership passes), but the case's executor
        // is still the STT (e.g. mid-transition data) — isTechExecution() must reject this.
        authenticateAs("tech@example.com", "ROLE_TECH");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .techId("tech-1")
                .techEmail("tech@example.com")
                .executorId("stt-1")
                .executorRole(ExecutorRole.STT)
                .executionMode(ExecutionMode.STT_SELF_EXECUTION)
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.setRdv("case-1", new SetRdvRequest(
                true, java.time.Instant.now(), null, null, null, false, null
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not assigned to you as the active executor");
    }

    @Test
    void setRdv_shouldAllowActiveTechExecutorAndMoveToRdvSet() {
        authenticateAs("tech@example.com", "ROLE_TECH");
        CaseService service = newService();
        stubSave();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .techId("tech-1")
                .techEmail("tech@example.com")
                .executorId("tech-1")
                .executorRole(ExecutorRole.TECH)
                .executionMode(ExecutionMode.TECH_EXECUTION)
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        CaseEntity result = service.setRdv("case-1", new SetRdvRequest(
                true, java.time.Instant.now(), null, null, null, false, null
        ));

        assertThat(result.getStatus()).isEqualTo(CaseStatus.RDV_SET);
    }

    @Test
    void setRdv_shouldMoveComplaintToRdvSetWithoutDiagnosisStep() {
        authenticateAs("tech@example.com", "ROLE_TECH");
        CaseService service = newService();
        stubSave();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("plt-1")
                .type(CaseType.PLAINTE)
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .techId("tech-1")
                .techEmail("tech@example.com")
                .executorId("tech-1")
                .executorRole(ExecutorRole.TECH)
                .executionMode(ExecutionMode.TECH_EXECUTION)
                .build();

        when(caseRepository.findById("plt-1")).thenReturn(Optional.of(caseEntity));

        CaseEntity result = service.setRdv("plt-1", new SetRdvRequest(
                true, java.time.Instant.now(), null, null, null, false, null
        ));

        assertThat(result.getStatus()).isEqualTo(CaseStatus.RDV_SET);
    }

    // ── validateByZm ─────────────────────────────────────────────────────────

    @Test
    void validateByZm_shouldRejectWhenCaseIsNotWorkDone() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.validateByZm("case-1", new ValidateZmRequest(true, "ok")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ZM validation is only allowed after work completion");
    }

    @Test
    void validateByZm_shouldApproveAndMoveToValidatedByZm() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();
        stubSave();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.WORK_DONE)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        CaseEntity result = service.validateByZm("case-1", new ValidateZmRequest(true, "looks good"));

        assertThat(result.getStatus()).isEqualTo(CaseStatus.VALIDATED_BY_ZM);
        org.mockito.Mockito.verify(sttStockService, org.mockito.Mockito.never())
                .releaseCaseUsageReservation(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void validateByZm_shouldRejectAndReleaseReservedStockWhenNotApproved() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();
        stubSave();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.WORK_DONE)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        CaseEntity result = service.validateByZm("case-1", new ValidateZmRequest(false, "redo it"));

        assertThat(result.getStatus()).isEqualTo(CaseStatus.REJECTED_BY_ZM);
        org.mockito.Mockito.verify(sttStockService).releaseCaseUsageReservation(
                caseEntity, "zm@example.com", "zm_rejected_before_final_validation", true
        );
    }

    // ── closeCase ────────────────────────────────────────────────────────────

    @Test
    void closeCase_shouldRejectWhenCaseIsNotValidatedByZm() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.WORK_DONE)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.closeCase("case-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only a case validated by ZM can be closed");
    }

    @Test
    void closeCase_shouldCloseWhenValidatedByZm() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        CaseService service = newService();
        stubSave();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.VALIDATED_BY_ZM)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        CaseEntity result = service.closeCase("case-1");

        assertThat(result.getStatus()).isEqualTo(CaseStatus.CLOSED);
    }

    @Test
    void closeCase_shouldRejectWhenCallerIsFromAnotherZone() {
        // Admin bypass aside, a ZM who is not the case's own zmEmail must not close it.
        authenticateAs("other-zm@example.com", "ROLE_ZM");
        CaseService service = newService();

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.VALIDATED_BY_ZM)
                .zmEmail("zm@example.com")
                .build();

        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.closeCase("case-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not the assigned ZM");
    }
}
