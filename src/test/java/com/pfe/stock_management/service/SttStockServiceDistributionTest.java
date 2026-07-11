package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.dto.DistributeToTechRequest;
import com.pfe.stock_management.dto.DistributionItemRequest;
import com.pfe.stock_management.entity.SttDistribution;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttDistributionRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import com.pfe.stock_management.repository.TechStockUsageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.pfe.support.SecurityTestSupport.authenticateAs;
import static com.pfe.support.SecurityTestSupport.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttStockServiceDistributionTest {

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
    @Mock
    private BusinessMetrics businessMetrics;

    @AfterEach
    void clearSecurityContext() {
        clear();
    }

    private SttStockService newService() {
        return new SttStockService(
                sttStockRepository, techStockRepository, distributionRepository, dpmRepository,
                userRepository, caseRepository, stockMovementService, notificationEventService,
                techStockUsageRepository, mongoTemplate, caseStockConsumptionService,
                stockAlertEvaluationService, stockUsagePermissionService, dpmAllocationService,
                equipmentUsagePolicy, equipmentInstanceService, equipmentRepository, businessMetrics
        );
    }

    private AppUser stt(String id, String email) {
        return AppUser.builder().id(id).email(email).fullName("STT One").role(UserRole.ROLE_STT).build();
    }

    private AppUser tech(String id, String assignedSttId) {
        return AppUser.builder()
                .id(id)
                .email("tech@example.com")
                .fullName("Tech One")
                .role(UserRole.ROLE_TECH)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .assignedSttId(assignedSttId)
                .build();
    }

    private SttStock stockWith(String sttId, int received, int distributed) {
        return SttStock.builder()
                .sttId(sttId)
                .items(new java.util.ArrayList<>(List.of(SttStockItem.builder()
                        .equipmentId("eq-1")
                        .equipmentName("ONT Box")
                        .unit("PCS")
                        .totalReceived(received)
                        .totalDistributedToTechs(distributed)
                        .build())))
                .build();
    }

    @Test
    void distributeToTech_shouldRejectWhenTechIsNotAssignedToCallingStt() {
        authenticateAs("stt@example.com", "ROLE_STT");
        SttStockService service = newService();

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt("stt-1", "stt@example.com")));
        when(userRepository.findByIdAndRoleAndStatusAndEnabled("tech-1", UserRole.ROLE_TECH, AccountStatus.APPROVED, true))
                .thenReturn(Optional.of(tech("tech-1", "stt-OTHER")));

        DistributeToTechRequest req = new DistributeToTechRequest();
        req.setTechId("tech-1");
        req.setItems(List.of());

        assertThatThrownBy(() -> service.distributeToTech(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not assigned to you");
    }

    @Test
    void distributeToTech_shouldRejectWhenRequestedQuantityExceedsAvailableStock() {
        authenticateAs("stt@example.com", "ROLE_STT");
        SttStockService service = newService();

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt("stt-1", "stt@example.com")));
        when(userRepository.findByIdAndRoleAndStatusAndEnabled("tech-1", UserRole.ROLE_TECH, AccountStatus.APPROVED, true))
                .thenReturn(Optional.of(tech("tech-1", "stt-1")));
        when(sttStockRepository.findBySttId("stt-1")).thenReturn(Optional.of(stockWith("stt-1", 10, 5))); // available = 5
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());

        DistributionItemRequest item = new DistributionItemRequest();
        item.setEquipmentId("eq-1");
        item.setQuantity(9); // exceeds available (5)

        DistributeToTechRequest req = new DistributeToTechRequest();
        req.setTechId("tech-1");
        req.setItems(List.of(item));

        assertThatThrownBy(() -> service.distributeToTech(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock");

        org.mockito.Mockito.verify(sttStockRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void distributeToTech_shouldRejectWhenEquipmentNotInCallerStock() {
        authenticateAs("stt@example.com", "ROLE_STT");
        SttStockService service = newService();

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt("stt-1", "stt@example.com")));
        when(userRepository.findByIdAndRoleAndStatusAndEnabled("tech-1", UserRole.ROLE_TECH, AccountStatus.APPROVED, true))
                .thenReturn(Optional.of(tech("tech-1", "stt-1")));
        when(sttStockRepository.findBySttId("stt-1")).thenReturn(Optional.of(stockWith("stt-1", 10, 0)));
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());

        DistributionItemRequest item = new DistributionItemRequest();
        item.setEquipmentId("eq-UNKNOWN");
        item.setQuantity(1);

        DistributeToTechRequest req = new DistributeToTechRequest();
        req.setTechId("tech-1");
        req.setItems(List.of(item));

        assertThatThrownBy(() -> service.distributeToTech(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Equipment not in your stock");
    }

    @Test
    void distributeToTech_shouldSucceedAndIncrementDistributedQuantity() {
        authenticateAs("stt@example.com", "ROLE_STT");
        SttStockService service = newService();

        SttStock stock = stockWith("stt-1", 10, 0);
        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt("stt-1", "stt@example.com")));
        when(userRepository.findByIdAndRoleAndStatusAndEnabled("tech-1", UserRole.ROLE_TECH, AccountStatus.APPROVED, true))
                .thenReturn(Optional.of(tech("tech-1", "stt-1")));
        when(sttStockRepository.findBySttId("stt-1")).thenReturn(Optional.of(stock));
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());
        when(distributionRepository.save(any(SttDistribution.class))).thenAnswer(inv -> {
            SttDistribution d = inv.getArgument(0);
            d.setId("dist-1");
            return d;
        });

        DistributionItemRequest item = new DistributionItemRequest();
        item.setEquipmentId("eq-1");
        item.setQuantity(3);

        DistributeToTechRequest req = new DistributeToTechRequest();
        req.setTechId("tech-1");
        req.setItems(List.of(item));

        SttDistribution result = service.distributeToTech(req);

        assertThat(result.getId()).isEqualTo("dist-1");
        assertThat(stock.getItems().get(0).getTotalDistributedToTechs()).isEqualTo(3);
        assertThat(stock.getItems().get(0).getAvailableQuantity()).isEqualTo(7);
        org.mockito.Mockito.verify(sttStockRepository).save(stock);
        org.mockito.Mockito.verify(stockMovementService).recordDistribution(result, "stt@example.com");
        org.mockito.Mockito.verify(notificationEventService).onStockDistributedToTech(result);
    }
}
