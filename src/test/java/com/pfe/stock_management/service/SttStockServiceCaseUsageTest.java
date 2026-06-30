package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.UsedEquipmentRequestItem;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.model.UsedEquipmentItem;
import com.pfe.case_management.model.WorkInfo;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttDistributionRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import com.pfe.stock_management.repository.TechStockUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttStockServiceCaseUsageTest {

    @Mock
    private SttStockRepository sttStockRepository;
    @Mock
    private TechStockRepository techStockRepository;
    @Mock
    private SttDistributionRepository distributionRepository;
    @Mock
    private DpmRepository dpmRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CaseRepository caseRepository;
    @Mock
    private StockMovementService stockMovementService;
    @Mock
    private NotificationEventService notificationEventService;
    @Mock
    private TechStockUsageRepository techStockUsageRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private CaseStockConsumptionService caseStockConsumptionService;
    @Mock
    private StockAlertEvaluationService stockAlertEvaluationService;
    @Mock
    private StockUsagePermissionService stockUsagePermissionService;
    @Mock
    private DpmAllocationService dpmAllocationService;
    @Mock
    private EquipmentUsagePolicyService equipmentUsagePolicy;
    @Mock
    private EquipmentInstanceService equipmentInstanceService;
    @Mock
    private EquipmentRepository equipmentRepository;

    @Test
    void syncCaseUsageForWorkSubmission_decrementsStockAndRecordsMovement() {
        SttStockService service = newService();
        AppUser technician = technician("tech-1", "tech@example.com");
        CaseEntity caseEntity = CaseEntity.builder().id("case-1").work(new WorkInfo()).build();
        TechStock techStock = techStock(technician.getId(), 10, 0);

        when(techStockRepository.findByTechId(technician.getId())).thenReturn(Optional.of(techStock));

        List<UsedEquipmentItem> storedItems = service.syncCaseUsageForWorkSubmission(
                caseEntity,
                technician,
                List.of(new UsedEquipmentRequestItem("eq-1", null, null, 2, null)),
                "tech@example.com"
        );

        assertEquals(8, techStock.getItems().get(0).getRemainingQuantity());
        assertEquals(2, techStock.getItems().get(0).getUsedQuantity());
        assertEquals(1, storedItems.size());
        assertEquals("eq-1", storedItems.get(0).getEquipmentId());
        assertEquals("Router", storedItems.get(0).getEquipmentName());
        assertEquals("PCS", storedItems.get(0).getUnit());
        assertEquals(2, storedItems.get(0).getQuantity());

        // Work-submission usage is persisted as an atomic $inc on the embedded array element
        // (not a full document save), so verify the actual persistence mechanism.
        verify(mongoTemplate).updateFirst(any(), any(), eq(TechStock.class));
        verify(stockMovementService).recordCaseUsage(eq(caseEntity), eq(technician), any(), eq(2), eq("tech@example.com"));
    }

    @Test
    void syncCaseUsageForWorkSubmission_rejectsWhenStockIsInsufficient() {
        SttStockService service = newService();
        AppUser technician = technician("tech-1", "tech@example.com");
        CaseEntity caseEntity = CaseEntity.builder().id("case-1").work(new WorkInfo()).build();
        TechStock techStock = techStock(technician.getId(), 8, 0);

        when(techStockRepository.findByTechId(technician.getId())).thenReturn(Optional.of(techStock));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.syncCaseUsageForWorkSubmission(
                        caseEntity,
                        technician,
                        List.of(new UsedEquipmentRequestItem("eq-1", null, null, 20, null)),
                        "tech@example.com"
                )
        );

        assertNotNull(ex.getReason());
        assertFalse(ex.getReason().isBlank());
        verify(techStockRepository, never()).save(any(TechStock.class));
        verify(stockMovementService, never()).recordCaseUsage(any(), any(), any(), anyInt(), any());
    }

    @Test
    void syncCaseUsageForWorkSubmission_doesNotDoubleDecrementWhenCaseIsResubmittedWithSameQuantity() {
        SttStockService service = newService();
        AppUser technician = technician("tech-1", "tech@example.com");
        WorkInfo workInfo = new WorkInfo();
        workInfo.setStockAppliedTechId(technician.getId());
        workInfo.setUsedEquipment(List.of(UsedEquipmentItem.builder()
                .equipmentId("eq-1")
                .equipmentName("Router")
                .unit("PCS")
                .quantity(2)
                .build()));

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .work(workInfo)
                .build();

        TechStock techStock = techStock(technician.getId(), 10, 2);

        when(techStockRepository.findByTechId(technician.getId())).thenReturn(Optional.of(techStock));

        List<UsedEquipmentItem> storedItems = service.syncCaseUsageForWorkSubmission(
                caseEntity,
                technician,
                List.of(new UsedEquipmentRequestItem("eq-1", null, null, 2, null)),
                "tech@example.com"
        );

        assertEquals(8, techStock.getItems().get(0).getRemainingQuantity());
        assertEquals(2, techStock.getItems().get(0).getUsedQuantity());
        assertEquals(2, storedItems.get(0).getQuantity());

        verify(techStockRepository, never()).save(any(TechStock.class));
        verify(stockMovementService, never()).recordCaseUsage(any(), any(), any(), anyInt(), any());
    }

    @Test
    void releaseCaseUsageReservation_restoresStockAndClearsAppliedTechnician() {
        SttStockService service = newService();
        AppUser technician = technician("tech-1", "tech@example.com");
        WorkInfo workInfo = new WorkInfo();
        workInfo.setStockAppliedTechId(technician.getId());
        workInfo.setUsedEquipment(List.of(UsedEquipmentItem.builder()
                .equipmentId("eq-1")
                .equipmentName("Router")
                .unit("PCS")
                .quantity(2)
                .build()));

        CaseEntity caseEntity = CaseEntity.builder()
                .id("case-1")
                .work(workInfo)
                .build();

        TechStock techStock = techStock(technician.getId(), 10, 2);

        when(techStockRepository.findByTechId(technician.getId())).thenReturn(Optional.of(techStock));
        when(techStockRepository.save(any(TechStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.releaseCaseUsageReservation(caseEntity, "zm@example.com", "zm_rejected_before_final_validation", true);

        assertEquals(10, techStock.getItems().get(0).getRemainingQuantity());
        assertEquals(0, techStock.getItems().get(0).getUsedQuantity());
        assertNull(caseEntity.getWork().getStockAppliedTechId());

        verify(techStockRepository).save(techStock);
    }

    private SttStockService newService() {
        return new SttStockService(
                sttStockRepository,
                techStockRepository,
                distributionRepository,
                dpmRepository,
                userRepository,
                caseRepository,
                stockMovementService,
                notificationEventService,
                techStockUsageRepository,
                mongoTemplate,
                caseStockConsumptionService,
                stockAlertEvaluationService,
                stockUsagePermissionService,
                dpmAllocationService,
                equipmentUsagePolicy,
                equipmentInstanceService,
                equipmentRepository
        );
    }

    private AppUser technician(String id, String email) {
        return AppUser.builder()
                .id(id)
                .email(email)
                .fullName("Tech User")
                .build();
    }

    private TechStock techStock(String techId, int receivedQuantity, int usedQuantity) {
        return TechStock.builder()
                .techId(techId)
                .items(List.of(TechStockItem.builder()
                        .equipmentId("eq-1")
                        .equipmentName("Router")
                        .unit("PCS")
                        .receivedQuantity(receivedQuantity)
                        .usedQuantity(usedQuantity)
                        .build()))
                .build();
    }
}
