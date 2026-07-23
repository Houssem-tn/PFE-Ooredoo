package com.pfe.case_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.DeclareClientRecoveryRequest;
import com.pfe.case_management.dto.RecoveredEquipmentRequestItem;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.ExecutionMode;
import com.pfe.case_management.enums.ExecutorRole;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.retrieved_stock.RecoveryDisposition;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItem;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItemStatus;
import com.pfe.stock_management.retrieved_stock.RetrievedStockService;
import com.pfe.stock_management.service.EquipmentUsagePolicyService;
import com.pfe.stock_management.service.SttStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code declareClientRecovery} used to be RES-only and always call
 * {@code MaterialReturnService.createCaseRecoveryReturn}; it now delegates to
 * {@link RetrievedStockService#recordRetrieval} and is available for both PLT and RES.
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceDeclareClientRecoveryTest {

    @Mock private CaseRepository caseRepository;
    @Mock private UserRepository userRepository;
    @Mock private SlaTrackingService slaTrackingService;
    @Mock private NotificationEventService notificationEventService;
    @Mock private FileStorageService fileStorageService;
    @Mock private CasePdfExportService casePdfExportService;
    @Mock private SttStockService sttStockService;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private EquipmentUsagePolicyService equipmentUsagePolicy;
    @Mock private RetrievedStockService retrievedStockService;
    @Mock private BusinessMetrics businessMetrics;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private CaseService newService() {
        return new CaseService(
                caseRepository, mongoTemplate, userRepository, slaTrackingService,
                notificationEventService, new CaseTimelineService(), fileStorageService,
                casePdfExportService, sttStockService, equipmentUsagePolicy,
                retrievedStockService, businessMetrics
        );
    }

    private void authenticateAsTech() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tech@example.com", "n/a", List.of(() -> "ROLE_TECH")));
    }

    private CaseEntity techExecutedCase(CaseType type, CaseModuleType module) {
        return CaseEntity.builder()
                .id("case-1").crmCase("CRM-1").type(type).caseModule(module)
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .techId("tech-1").techEmail("tech@example.com")
                .executorId("tech-1").executorRole(ExecutorRole.TECH).executionMode(ExecutionMode.TECH_EXECUTION)
                .build();
    }

    private AppUser technician() {
        return AppUser.builder().id("tech-1").email("tech@example.com").role(UserRole.ROLE_TECH).build();
    }

    private RecoveredEquipmentRequestItem retrievedLine() {
        return new RecoveredEquipmentRequestItem(
                "eq-1", "Old Modem", "PIECES", 1, null, null, null, "DAMAGED", "RETRIEVED", null);
    }

    private RetrievedStockItem createdItem() {
        return RetrievedStockItem.builder()
                .id("rsi-1").equipmentName("Old Modem").unit("PIECES")
                .disposition(RecoveryDisposition.RETRIEVED)
                .status(RetrievedStockItemStatus.PENDING_COLLECTION)
                .build();
    }

    @Test
    void declareClientRecovery_pltCase_delegatesToRetrievedStockServiceAndPersists() {
        authenticateAsTech();
        CaseEntity caseEntity = techExecutedCase(CaseType.PLAINTE, null);
        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findByEmail("tech@example.com")).thenReturn(Optional.of(technician()));
        when(retrievedStockService.recordRetrieval(any(), any(), any(), any()))
                .thenReturn(List.of(createdItem()));
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclareClientRecoveryRequest request = new DeclareClientRecoveryRequest(List.of(retrievedLine()), "comment");
        CaseEntity result = newService().declareClientRecovery("case-1", request);

        assertThat(result.getWork()).isNotNull();
        assertThat(result.getWork().getRecoveredEquipment()).hasSize(1);
        assertThat(result.getWork().getRecoveredEquipment().get(0).getRetrievedStockItemId()).isEqualTo("rsi-1");
        assertThat(result.getWork().getRecoveredEquipment().get(0).getDisposition()).isEqualTo("RETRIEVED");
        assertThat(result.getTimeline()).anyMatch(e -> e.getEventType() == CaseTimelineEventType.CLIENT_MATERIAL_RECOVERED);
    }

    @Test
    void declareClientRecovery_resCase_stillSucceeds() {
        authenticateAsTech();
        CaseEntity caseEntity = techExecutedCase(CaseType.PLAINTE, CaseModuleType.RES);
        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findByEmail("tech@example.com")).thenReturn(Optional.of(technician()));
        when(retrievedStockService.recordRetrieval(any(), any(), any(), any()))
                .thenReturn(List.of(createdItem()));
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclareClientRecoveryRequest request = new DeclareClientRecoveryRequest(List.of(retrievedLine()), null);
        CaseEntity result = newService().declareClientRecovery("case-1", request);

        assertThat(result.getWork().getRecoveredEquipment()).hasSize(1);
    }

    @Test
    void declareClientRecovery_nonPhysicalDisposition_mirroredWithNoRetrievedStockItemId() {
        authenticateAsTech();
        CaseEntity caseEntity = techExecutedCase(CaseType.PLAINTE, null);
        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findByEmail("tech@example.com")).thenReturn(Optional.of(technician()));
        // MISSING disposition -> RetrievedStockService returns a null at that position.
        when(retrievedStockService.recordRetrieval(any(), any(), any(), any()))
                .thenReturn(java.util.Arrays.asList((RetrievedStockItem) null));
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RecoveredEquipmentRequestItem missingLine = new RecoveredEquipmentRequestItem(
                "eq-1", "Old Modem", "PIECES", 1, null, null, null, null, "MISSING", "client denies");
        DeclareClientRecoveryRequest request = new DeclareClientRecoveryRequest(List.of(missingLine), null);
        CaseEntity result = newService().declareClientRecovery("case-1", request);

        assertThat(result.getWork().getRecoveredEquipment()).hasSize(1);
        assertThat(result.getWork().getRecoveredEquipment().get(0).getRetrievedStockItemId()).isNull();
        assertThat(result.getWork().getRecoveredEquipment().get(0).getDisposition()).isEqualTo("MISSING");
    }

    @Test
    void declareClientRecovery_accumulatesAcrossRepeatedDeclarations() {
        authenticateAsTech();
        CaseEntity caseEntity = techExecutedCase(CaseType.PLAINTE, null);
        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        when(userRepository.findByEmail("tech@example.com")).thenReturn(Optional.of(technician()));
        when(retrievedStockService.recordRetrieval(any(), any(), any(), any()))
                .thenReturn(List.of(createdItem()));
        when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclareClientRecoveryRequest request = new DeclareClientRecoveryRequest(List.of(retrievedLine()), null);
        CaseService service = newService();

        CaseEntity afterFirst = service.declareClientRecovery("case-1", request);
        assertThat(afterFirst.getWork().getRecoveredEquipment()).hasSize(1);

        CaseEntity afterSecond = service.declareClientRecovery("case-1", request);
        assertThat(afterSecond.getWork().getRecoveredEquipment()).hasSize(2);
    }

    @Test
    void declareClientRecovery_rejectsEmptyItemsWithoutCallingRetrievedStockService() {
        authenticateAsTech();
        CaseEntity caseEntity = techExecutedCase(CaseType.PLAINTE, null);
        when(caseRepository.findById("case-1")).thenReturn(Optional.of(caseEntity));
        // Deliberately not stubbing userRepository.findByEmail: the empty-items check must throw
        // before the declarer is even resolved, so stubbing it here would be unnecessary/unused.

        DeclareClientRecoveryRequest request = new DeclareClientRecoveryRequest(List.of(), null);

        assertThatThrownBy(() -> newService().declareClientRecovery("case-1", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Declare at least one recovered item");
    }
}
