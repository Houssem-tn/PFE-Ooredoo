package com.pfe.stock_management.retrieved_stock;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.FileStorageService;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.dto.RecoveredEquipmentRequestItem;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.stock_management.entity.Equipment;
import com.pfe.stock_management.enums.TrackingMode;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.service.EquipmentInstanceService;
import com.pfe.stock_management.service.EquipmentUsagePolicyService;
import com.pfe.stock_management.service.StockMovementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievedStockServiceTest {

    @Mock private RetrievedStockItemRepository repository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private UserRepository userRepository;
    @Mock private EquipmentInstanceService equipmentInstanceService;
    @Mock private EquipmentUsagePolicyService equipmentUsagePolicy;
    @Mock private StockMovementService stockMovementService;
    @Mock private NotificationEventService notificationEventService;
    @Mock private CaseTimelineService caseTimelineService;
    @Mock private FileStorageService fileStorageService;

    private RetrievedStockService newService() {
        return new RetrievedStockService(
                repository, equipmentRepository, caseRepository, userRepository,
                equipmentInstanceService, equipmentUsagePolicy, stockMovementService,
                notificationEventService, caseTimelineService, fileStorageService
        );
    }

    private CaseEntity pltCase() {
        return CaseEntity.builder()
                .id("case-1").crmCase("CRM-1").type(CaseType.PLAINTE)
                .clientName("Client A").sttId("stt-1").zmId("zm-1")
                .build();
    }

    private AppUser stt() {
        return AppUser.builder().id("stt-1").fullName("STT One").email("stt@x.com").build();
    }

    private AppUser zm() {
        return AppUser.builder().id("zm-1").fullName("ZM One").email("zm@x.com").build();
    }

    private AppUser tech() {
        return AppUser.builder().id("tech-1").fullName("Tech One").email("tech@x.com").build();
    }

    private void stubUsers() {
        when(userRepository.findById("stt-1")).thenReturn(Optional.of(stt()));
        when(userRepository.findById("zm-1")).thenReturn(Optional.of(zm()));
        when(equipmentUsagePolicy.resolveContext(any())).thenReturn(EquipmentUsagePolicyService.UsageContext.PLT);
    }

    private void stubSaveGeneratesId() {
        AtomicInteger counter = new AtomicInteger();
        when(repository.save(any(RetrievedStockItem.class))).thenAnswer(inv -> {
            RetrievedStockItem item = inv.getArgument(0);
            if (item.getId() == null) {
                item.setId("rsi-" + counter.incrementAndGet());
            }
            return item;
        });
    }

    // ── recordRetrieval ────────────────────────────────────────────────────────

    @Test
    void recordRetrieval_rejectsEmptyItemList() {
        assertThatThrownBy(() -> newService().recordRetrieval(pltCase(), tech(), List.of(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Declare at least one recovered item");
    }

    @Test
    void recordRetrieval_createsItemForRetrievedDisposition_nonTracked() {
        Equipment modem = Equipment.builder().id("eq-1").name("Modem").unit(null)
                .trackingMode(TrackingMode.NONE).recoverableFromClient(true).build();
        when(equipmentRepository.findAllById(List.of("eq-1"))).thenReturn(List.of(modem));
        stubUsers();
        stubSaveGeneratesId();

        RecoveredEquipmentRequestItem reqItem = new RecoveredEquipmentRequestItem(
                "eq-1", "Modem", "PIECES", 2, null, null, null, "GOOD", "RETRIEVED", null);

        List<RetrievedStockItem> result = newService().recordRetrieval(pltCase(), tech(), List.of(reqItem), "note");

        assertThat(result).hasSize(1);
        RetrievedStockItem created = result.get(0);
        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(RetrievedStockItemStatus.PENDING_COLLECTION);
        assertThat(created.getDisposition()).isEqualTo(RecoveryDisposition.RETRIEVED);
        assertThat(created.getTechnicianId()).isEqualTo("tech-1");
        assertThat(created.getZmId()).isEqualTo("zm-1");
        assertThat(created.getQuantity()).isEqualTo(2);
        verify(equipmentInstanceService, never()).recordTechRetrieval(any(), any(), any(), any(), any(), any());
        verify(stockMovementService).recordRetrievedStockDeclared(eq(created), eq("tech@x.com"));
        verify(notificationEventService).onRetrievedStockDeclared(any(), anyList());
    }

    @Test
    void recordRetrieval_nonPhysicalDispositionCreatesNothing() {
        Equipment modem = Equipment.builder().id("eq-1").name("Modem")
                .trackingMode(TrackingMode.NONE).recoverableFromClient(true).build();
        when(equipmentRepository.findAllById(List.of("eq-1"))).thenReturn(List.of(modem));
        stubUsers();

        RecoveredEquipmentRequestItem missingItem = new RecoveredEquipmentRequestItem(
                "eq-1", "Modem", "PIECES", 1, null, null, null, null, "MISSING", "client denies");

        List<RetrievedStockItem> result = newService().recordRetrieval(pltCase(), tech(), List.of(missingItem), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isNull();
        verify(repository, never()).save(any());
        verify(notificationEventService, never()).onRetrievedStockDeclared(any(), anyList());
    }

    @Test
    void recordRetrieval_rejectsDuplicateSerialWithinSameSubmission() {
        RecoveredEquipmentRequestItem line1 = new RecoveredEquipmentRequestItem(
                "eq-1", "ODU", "PIECES", 1, "SN-DUP", null, null, "GOOD", "RETRIEVED", null);
        RecoveredEquipmentRequestItem line2 = new RecoveredEquipmentRequestItem(
                "eq-1", "ODU", "PIECES", 1, "SN-DUP", null, null, "GOOD", "RETRIEVED", null);

        assertThatThrownBy(() -> newService().recordRetrieval(pltCase(), tech(), List.of(line1, line2), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("repeated in this submission");
        verify(repository, never()).save(any());
    }

    @Test
    void recordRetrieval_rejectsTrackedEquipmentWithQuantityGreaterThanOne() {
        Equipment odu = Equipment.builder().id("eq-1").name("ODU P90")
                .trackingMode(TrackingMode.SERIAL_ONLY).recoverableFromClient(true).build();
        when(equipmentRepository.findAllById(List.of("eq-1"))).thenReturn(List.of(odu));
        stubUsers();

        RecoveredEquipmentRequestItem line = new RecoveredEquipmentRequestItem(
                "eq-1", "ODU P90", "PIECES", 2, "SN-1", null, null, "GOOD", "RETRIEVED", null);

        assertThatThrownBy(() -> newService().recordRetrieval(pltCase(), tech(), List.of(line), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("quantity must be 1");
    }

    @Test
    void recordRetrieval_rejectsTrackedEquipmentMissingRequiredSerial() {
        Equipment odu = Equipment.builder().id("eq-1").name("ODU P90")
                .trackingMode(TrackingMode.SERIAL_ONLY).recoverableFromClient(true).build();
        when(equipmentRepository.findAllById(List.of("eq-1"))).thenReturn(List.of(odu));
        stubUsers();

        RecoveredEquipmentRequestItem line = new RecoveredEquipmentRequestItem(
                "eq-1", "ODU P90", "PIECES", 1, null, null, null, "GOOD", "RETRIEVED", null);

        assertThatThrownBy(() -> newService().recordRetrieval(pltCase(), tech(), List.of(line), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Serial Number is required");
    }

    @Test
    void recordRetrieval_trackedEquipmentLinksInstance() {
        Equipment odu = Equipment.builder().id("eq-1").name("ODU P90")
                .trackingMode(TrackingMode.SERIAL_ONLY).recoverableFromClient(true).build();
        when(equipmentRepository.findAllById(List.of("eq-1"))).thenReturn(List.of(odu));
        stubUsers();
        stubSaveGeneratesId();
        when(equipmentInstanceService.recordTechRetrieval(
                eq(odu), any(), eq((String) null), eq("SN-1"), any(), eq("tech@x.com")))
                .thenReturn("inst-99");

        RecoveredEquipmentRequestItem line = new RecoveredEquipmentRequestItem(
                "eq-1", "ODU P90", "PIECES", 1, "SN-1", null, null, "GOOD", "RETRIEVED", null);

        List<RetrievedStockItem> result = newService().recordRetrieval(pltCase(), tech(), List.of(line), null);

        assertThat(result.get(0).getInstanceId()).isEqualTo("inst-99");
    }

    // ── confirmCollection ──────────────────────────────────────────────────────

    private RetrievedStockItem pendingItem(String id, String caseId, String technicianId, String instanceId) {
        return RetrievedStockItem.builder()
                .id(id).caseId(caseId).technicianId(technicianId).technicianEmail(technicianId + "@x.com")
                .zmId("zm-1").instanceId(instanceId)
                .status(RetrievedStockItemStatus.PENDING_COLLECTION)
                .quantity(1)
                .build();
    }

    @Test
    void confirmCollection_marksItemsCollectedAndSendsOneNotificationPerTechnician() {
        RetrievedStockItem item1 = pendingItem("rsi-1", "case-1", "tech-1", "inst-1");
        RetrievedStockItem item2 = pendingItem("rsi-2", "case-1", "tech-1", null);
        when(repository.findAllById(List.of("rsi-1", "rsi-2"))).thenReturn(List.of(item1, item2));
        when(repository.save(any(RetrievedStockItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmCollectionRequest request = new ConfirmCollectionRequest(List.of("rsi-1", "rsi-2"), "picked up");
        ConfirmCollectionResponse response = newService().confirmCollection(zm(), request);

        assertThat(response.getCollectedItems()).hasSize(2);
        assertThat(item1.getStatus()).isEqualTo(RetrievedStockItemStatus.COLLECTED);
        assertThat(item1.getCollectedByZmId()).isEqualTo("zm-1");
        assertThat(item2.getStatus()).isEqualTo(RetrievedStockItemStatus.COLLECTED);

        verify(equipmentInstanceService).markCollectedByZm(List.of("inst-1"), "zm-1", "ZM One", "zm@x.com");
        verify(stockMovementService, times(2)).recordRetrievedStockCollected(any(), eq("zm@x.com"));
        // Both items belong to the same technician -> exactly one aggregated notification.
        verify(notificationEventService, times(1)).onRetrievedStockCollected(anyList());
    }

    @Test
    void confirmCollection_rejectsItemOutsideZmScope() {
        RetrievedStockItem foreign = pendingItem("rsi-1", "case-1", "tech-1", null);
        foreign.setZmId("zm-2");
        when(repository.findAllById(List.of("rsi-1"))).thenReturn(List.of(foreign));

        ConfirmCollectionRequest request = new ConfirmCollectionRequest(List.of("rsi-1"), null);

        assertThatThrownBy(() -> newService().confirmCollection(zm(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not in your scope");
        verify(repository, never()).save(any());
    }

    @Test
    void confirmCollection_rejectsWholeBatchWhenOneItemAlreadyCollected() {
        RetrievedStockItem stillPending = pendingItem("rsi-1", "case-1", "tech-1", null);
        RetrievedStockItem alreadyCollected = pendingItem("rsi-2", "case-1", "tech-1", null);
        alreadyCollected.setStatus(RetrievedStockItemStatus.COLLECTED);
        when(repository.findAllById(List.of("rsi-1", "rsi-2"))).thenReturn(List.of(stillPending, alreadyCollected));

        ConfirmCollectionRequest request = new ConfirmCollectionRequest(List.of("rsi-1", "rsi-2"), null);

        assertThatThrownBy(() -> newService().confirmCollection(zm(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no longer pending collection");
        // Whole batch rejected before any mutation -> neither item saved.
        verify(repository, never()).save(any());
        assertThat(stillPending.getStatus()).isEqualTo(RetrievedStockItemStatus.PENDING_COLLECTION);
    }

    @Test
    void confirmCollection_rejectsUnknownItemId() {
        when(repository.findAllById(List.of("missing"))).thenReturn(List.of());

        ConfirmCollectionRequest request = new ConfirmCollectionRequest(List.of("missing"), null);

        assertThatThrownBy(() -> newService().confirmCollection(zm(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("were not found");
    }
}
