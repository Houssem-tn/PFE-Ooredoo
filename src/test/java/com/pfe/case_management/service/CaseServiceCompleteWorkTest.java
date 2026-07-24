package com.pfe.case_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.CompleteWorkRequest;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ExecutionMode;
import com.pfe.case_management.enums.ExecutorRole;
import com.pfe.case_management.model.TechnicalMetrics;
import com.pfe.case_management.model.UsedEquipmentItem;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.retrieved_stock.RetrievedStockService;
import com.pfe.stock_management.service.EquipmentUsagePolicyService;
import com.pfe.stock_management.service.SttStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseServiceCompleteWorkTest {

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
        SecurityContextHolder.clearContext();
    }

    @Test
    void completeWork_persistsUsedEquipmentAndTechStockReservationMarker() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tech@example.com",
                        "n/a",
                        List.of(() -> "ROLE_TECH")
                )
        );

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

        AppUser technician = AppUser.builder()
                .id("tech-1")
                .email("tech@example.com")
                .role(UserRole.ROLE_TECH)
                .build();

        List<UsedEquipmentItem> usedEquipment = List.of(UsedEquipmentItem.builder()
                .equipmentId("eq-1")
                .equipmentName("Router")
                .unit("PCS")
                .quantity(2)
                .build());

        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(userRepository.findById("tech-1")).thenReturn(Optional.of(technician));
        when(sttStockService.syncCaseUsageForWorkSubmission(any(), any(), any(), any())).thenReturn(usedEquipment);
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaseService service = new CaseService(
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

        CompleteWorkRequest request = new CompleteWorkRequest(
                "OK",
                Instant.parse("2026-05-07T10:15:30Z"),
                "Installed equipment",
                12.5,
                1,
                30.0,
                10.0,
                -90.0,
                -8.0,
                20.0,
                36.8,
                10.1,
                Instant.parse("2026-05-07T10:15:00Z"),
                3.0,
                List.of(new com.pfe.case_management.dto.UsedEquipmentRequestItem("eq-1", null, null, 2, null))
        );

        service.completeWork(caseEntity.getId(), request);

        ArgumentCaptor<CaseEntity> savedCase = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(savedCase.capture());

        assertEquals(CaseStatus.WORK_DONE, savedCase.getValue().getStatus());
        assertEquals(1, savedCase.getValue().getWork().getUsedEquipment().size());
        assertEquals(2, savedCase.getValue().getWork().getUsedEquipment().get(0).getQuantity());
        assertEquals("tech-1", savedCase.getValue().getWork().getStockAppliedTechId());
    }

    /**
     * Résiliation (PLAINTE + RES import source) must complete with no technician metrics and no
     * diagnosis on the case: the RES import source bypasses the complaint diagnosis prerequisite,
     * and every metric field may be null.
     */
    @Test
    void completeWork_resiliationSucceedsWithoutMetricsOrDiagnosis() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tech@example.com",
                        "n/a",
                        List.of(() -> "ROLE_TECH")
                )
        );

        CaseEntity resCase = CaseEntity.builder()
                .id("res-1")
                .type(CaseType.PLAINTE)
                .source("RES_TODO_IMPORT")
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .techId("tech-1")
                .techEmail("tech@example.com")
                .executorId("tech-1")
                .executorRole(ExecutorRole.TECH)
                .executionMode(ExecutionMode.TECH_EXECUTION)
                .build();

        AppUser technician = AppUser.builder()
                .id("tech-1")
                .email("tech@example.com")
                .role(UserRole.ROLE_TECH)
                .build();

        when(caseRepository.findById(resCase.getId())).thenReturn(Optional.of(resCase));
        when(userRepository.findById("tech-1")).thenReturn(Optional.of(technician));
        // RES sends no consumed equipment (empty list) — no stock is applied.
        when(sttStockService.syncCaseUsageForWorkSubmission(any(), any(), any(), any())).thenReturn(List.of());
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaseService service = new CaseService(
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

        // All metric fields null; no used equipment; no diagnosis set on the case.
        CompleteWorkRequest request = new CompleteWorkRequest(
                "Résiliation done",
                Instant.parse("2026-05-07T10:15:30Z"),
                "Recovered client equipment",
                null, null, null, null, null, null, null,
                null, null, null, null,
                List.of()
        );

        service.completeWork(resCase.getId(), request);

        ArgumentCaptor<CaseEntity> savedCase = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(savedCase.capture());

        // Complaint/RES completion moves to RESOLUTION_SUBMITTED and carries no metrics.
        assertEquals(CaseStatus.RESOLUTION_SUBMITTED, savedCase.getValue().getStatus());
        assertEquals(null, savedCase.getValue().getMetrics().getNombrePv());
        assertEquals(null, savedCase.getValue().getMetrics().getRsrp());
        assertEquals(null, savedCase.getValue().getMetrics().getDebitDl());
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

    private void authenticateTech() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tech@example.com", "n/a", List.of(() -> "ROLE_TECH")));
    }

    /** Complaint metrics (download/upload/SINR/RSRP/RSRQ/PV) must persist exactly like Activation. */
    @Test
    void completeWork_manualComplaintSucceedsWithoutDiagnosis() {
        authenticateTech();

        CaseEntity complaint = CaseEntity.builder()
                .id("plt-manual-1")
                .type(CaseType.PLAINTE)
                .status(CaseStatus.RDV_SET)
                .techId("tech-1")
                .techEmail("tech@example.com")
                .executorId("tech-1")
                .executorRole(ExecutorRole.TECH)
                .executionMode(ExecutionMode.TECH_EXECUTION)
                .build();

        AppUser technician = AppUser.builder().id("tech-1").email("tech@example.com").role(UserRole.ROLE_TECH).build();

        when(caseRepository.findById("plt-manual-1")).thenReturn(Optional.of(complaint));
        when(userRepository.findById("tech-1")).thenReturn(Optional.of(technician));
        when(sttStockService.syncCaseUsageForWorkSubmission(any(), any(), any(), any())).thenReturn(List.of());
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CompleteWorkRequest request = new CompleteWorkRequest(
                "Resolved", Instant.parse("2026-05-07T10:15:30Z"), "complaint handled on site",
                null, 1, 25.0, 8.0, -91.0, -9.0, 18.0,
                null, null, null, null, List.of());

        newService().completeWork("plt-manual-1", request);

        ArgumentCaptor<CaseEntity> saved = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(saved.capture());

        assertEquals(CaseStatus.RESOLUTION_SUBMITTED, saved.getValue().getStatus());
        assertEquals(25.0, saved.getValue().getMetrics().getDebitDl(), 0.0001);
        assertEquals(8.0, saved.getValue().getMetrics().getDebitUl(), 0.0001);
        assertEquals(-91.0, saved.getValue().getMetrics().getRsrp(), 0.0001);
        assertEquals(Integer.valueOf(1), saved.getValue().getMetrics().getNombrePv());
    }

    @Test
    void completeWork_complaintPersistsMetrics() {
        authenticateTech();

        CaseEntity complaint = CaseEntity.builder()
                .id("plt-1")
                .type(CaseType.PLAINTE)
                .source("PLT_TODO_IMPORT") // field-imported complaint → no diagnosis prerequisite
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .techId("tech-1")
                .techEmail("tech@example.com")
                .executorId("tech-1")
                .executorRole(ExecutorRole.TECH)
                .executionMode(ExecutionMode.TECH_EXECUTION)
                .build();

        AppUser technician = AppUser.builder().id("tech-1").email("tech@example.com").role(UserRole.ROLE_TECH).build();

        when(caseRepository.findById("plt-1")).thenReturn(Optional.of(complaint));
        when(userRepository.findById("tech-1")).thenReturn(Optional.of(technician));
        when(sttStockService.syncCaseUsageForWorkSubmission(any(), any(), any(), any())).thenReturn(List.of());
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CompleteWorkRequest request = new CompleteWorkRequest(
                "Fixed", Instant.parse("2026-05-07T10:15:30Z"), "resolved",
                null, 2, 30.0, 10.0, -90.0, -8.0, 20.0,
                null, null, null, null, List.of());

        newService().completeWork("plt-1", request);

        ArgumentCaptor<CaseEntity> saved = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(saved.capture());

        assertEquals(CaseStatus.RESOLUTION_SUBMITTED, saved.getValue().getStatus());
        assertEquals(30.0, saved.getValue().getMetrics().getDebitDl(), 0.0001);
        assertEquals(10.0, saved.getValue().getMetrics().getDebitUl(), 0.0001);
        assertEquals(20.0, saved.getValue().getMetrics().getSinr(), 0.0001);
        assertEquals(-90.0, saved.getValue().getMetrics().getRsrp(), 0.0001);
        assertEquals(Integer.valueOf(2), saved.getValue().getMetrics().getNombrePv());
    }

    /**
     * A later Complaint completion that carries no metrics (null fields) must NOT wipe metrics that
     * were already saved — the merge preserves existing values, and a valid 0 is written.
     */
    @Test
    void completeWork_complaintDoesNotOverwriteExistingMetricsWithNulls() {
        authenticateTech();

        TechnicalMetrics existing = new TechnicalMetrics();
        existing.setDebitDl(45.0);
        existing.setSinr(0.0); // a genuine zero must survive
        existing.setRsrp(-88.0);

        CaseEntity complaint = CaseEntity.builder()
                .id("plt-2")
                .type(CaseType.PLAINTE)
                .source("PLT_TODO_IMPORT")
                .status(CaseStatus.REOPENED) // allowed for complaint re-completion
                .techId("tech-1")
                .techEmail("tech@example.com")
                .executorId("tech-1")
                .executorRole(ExecutorRole.TECH)
                .executionMode(ExecutionMode.TECH_EXECUTION)
                .metrics(existing)
                .build();

        AppUser technician = AppUser.builder().id("tech-1").email("tech@example.com").role(UserRole.ROLE_TECH).build();

        when(caseRepository.findById("plt-2")).thenReturn(Optional.of(complaint));
        when(userRepository.findById("tech-1")).thenReturn(Optional.of(technician));
        when(sttStockService.syncCaseUsageForWorkSubmission(any(), any(), any(), any())).thenReturn(List.of());
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // No metrics in this submission (e.g. an equipment-only re-completion).
        CompleteWorkRequest request = new CompleteWorkRequest(
                "Updated", Instant.parse("2026-05-08T09:00:00Z"), "note",
                null, null, null, null, null, null, null,
                null, null, null, null, List.of());

        newService().completeWork("plt-2", request);

        ArgumentCaptor<CaseEntity> saved = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(saved.capture());

        // Existing metrics preserved (including the zero SINR).
        assertEquals(45.0, saved.getValue().getMetrics().getDebitDl(), 0.0001);
        assertEquals(0.0, saved.getValue().getMetrics().getSinr(), 0.0001);
        assertEquals(-88.0, saved.getValue().getMetrics().getRsrp(), 0.0001);
    }
}
