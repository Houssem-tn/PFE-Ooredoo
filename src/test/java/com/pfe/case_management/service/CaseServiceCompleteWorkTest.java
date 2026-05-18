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
import com.pfe.case_management.model.UsedEquipmentItem;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.stock_management.service.SttStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
                userRepository,
                slaTrackingService,
                notificationEventService,
                new CaseTimelineService(),
                fileStorageService,
                casePdfExportService,
                sttStockService
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
                List.of(new com.pfe.case_management.dto.UsedEquipmentRequestItem("eq-1", null, null, 2))
        );

        service.completeWork(caseEntity.getId(), request);

        ArgumentCaptor<CaseEntity> savedCase = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(savedCase.capture());

        assertEquals(CaseStatus.WORK_DONE, savedCase.getValue().getStatus());
        assertEquals(1, savedCase.getValue().getWork().getUsedEquipment().size());
        assertEquals(2, savedCase.getValue().getWork().getUsedEquipment().get(0).getQuantity());
        assertEquals("tech-1", savedCase.getValue().getWork().getStockAppliedTechId());
    }
}
