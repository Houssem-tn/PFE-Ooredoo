package com.pfe.stock_management.material_return;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.repository.CaseRepository;
import com.pfe.case_management.service.CaseTimelineService;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItem;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItemRepository;
import com.pfe.stock_management.retrieved_stock.RetrievedStockItemStatus;
import com.pfe.stock_management.service.EquipmentInstanceService;
import com.pfe.stock_management.service.StockMovementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pfe.support.SecurityTestSupport.authenticateAs;
import static com.pfe.support.SecurityTestSupport.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialReturnServiceTest {

    @Mock private MaterialReturnRepository returnRepository;
    @Mock private SttStockRepository sttStockRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private RetrievedStockItemRepository retrievedStockItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private DpmRepository dpmRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private CaseTimelineService caseTimelineService;
    @Mock private StockMovementService stockMovementService;
    @Mock private NotificationEventService notificationEventService;
    @Mock private EquipmentInstanceService equipmentInstanceService;
    @Mock private BusinessMetrics businessMetrics;

    @AfterEach
    void clearSecurityContext() {
        clear();
    }

    private MaterialReturnService newService() {
        return new MaterialReturnService(
                returnRepository, sttStockRepository, equipmentRepository, retrievedStockItemRepository,
                userRepository, dpmRepository, caseRepository, caseTimelineService,
                stockMovementService, notificationEventService, equipmentInstanceService, businessMetrics
        );
    }

    private AppUser stt() {
        return AppUser.builder()
                .id("stt-1")
                .email("stt@example.com")
                .fullName("STT One")
                .role(UserRole.ROLE_STT)
                .build();
    }

    private AppUser zm() {
        return AppUser.builder()
                .id("zm-1")
                .email("zm@example.com")
                .fullName("ZM One")
                .role(UserRole.ROLE_ZM)
                .canCreateDpm(true)
                .build();
    }

    private CreateMaterialReturnDto.CreateMaterialReturnDtoBuilder baseSttStockRequest() {
        return CreateMaterialReturnDto.builder()
                .responsibleZmId("zm-1")
                .returnSource(ReturnSource.STT_STOCK)
                .reason(ReturnReason.UNUSED)
                .condition(ReturnCondition.GOOD)
                .destination(ReturnDestination.USABLE_STOCK)
                .items(List.of(ReturnedItemDto.builder().equipmentId("eq-1").quantity(2).build()));
    }

    private CreateMaterialReturnDto.CreateMaterialReturnDtoBuilder baseCollectedRequest(String... ids) {
        return CreateMaterialReturnDto.builder()
                .responsibleZmId("zm-1")
                .returnSource(ReturnSource.COLLECTED_ITEM_STOCK)
                .reason(ReturnReason.UNUSED)
                .condition(ReturnCondition.GOOD)
                .destination(ReturnDestination.USABLE_STOCK)
                .retrievedStockItemIds(List.of(ids))
                .items(List.of());
    }

    private RetrievedStockItem collectedItem(String id, RetrievedStockItemStatus status) {
        return RetrievedStockItem.builder()
                .id(id)
                .equipmentId("eq-" + id)
                .equipmentName("Collected " + id)
                .articleCode("ART-" + id)
                .unit("PCS")
                .quantity(1)
                .serialNumber("SN-" + id)
                .imei("IMEI-" + id)
                .technicianId("tech-1")
                .technicianName("Tech One")
                .technicianEmail("tech@example.com")
                .sttId("stt-1")
                .sttName("STT One")
                .sttEmail("stt@example.com")
                .collectedBySttId("stt-1")
                .collectedBySttName("STT One")
                .caseId("case-" + id)
                .crmCase("CRM-" + id)
                .caseUsageContext("PLT")
                .clientName("Client " + id)
                .clientPhone("555-" + id)
                .status(status)
                .retrievedAt(Instant.parse("2026-07-01T10:15:30Z"))
                .collectedAt(Instant.parse("2026-07-02T11:15:30Z"))
                .build();
    }

    private MaterialReturnRequest collectedReturn(MaterialReturnStatus status) {
        return MaterialReturnRequest.builder()
                .id("return-1")
                .returnCode("RET-2026-0001")
                .sttId("stt-1")
                .sttName("STT One")
                .sttEmail("stt@example.com")
                .zmId("zm-1")
                .zmName("ZM One")
                .zmEmail("zm@example.com")
                .returnSource(ReturnSource.COLLECTED_ITEM_STOCK)
                .reason(ReturnReason.UNUSED)
                .condition(ReturnCondition.GOOD)
                .destination(ReturnDestination.USABLE_STOCK)
                .status(status)
                .proposedHandoverDate("2026-07-25")
                .proposedHandoverTime("10:00")
                .items(new ArrayList<>(List.of(
                        ReturnedItem.builder()
                                .equipmentId("eq-1")
                                .equipmentName("Collected Item")
                                .quantity(1)
                                .unit("PCS")
                                .retrievedStockItemId("rsi-1")
                                .serialNumber("SN-rsi-1")
                                .originalTechnicianName("Tech One")
                                .originalCaseReference("CRM-rsi-1")
                                .originalCustomerName("Client A")
                                .sttCollectedAt(Instant.parse("2026-07-02T11:15:30Z"))
                                .build()
                )))
                .timeline(new ArrayList<>())
                .createdAt(Instant.parse("2026-07-23T08:00:00Z"))
                .submittedAt(Instant.parse("2026-07-23T08:00:00Z"))
                .build();
    }

    @Test
    void createReturn_shouldRejectOtherReasonWithoutComment() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();
        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));

        CreateMaterialReturnDto req = baseSttStockRequest().reason(ReturnReason.OTHER).comment(null).build();

        assertThatThrownBy(() -> service.createReturn(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Comment is required when return reason is OTHER");
    }

    @Test
    void createReturn_shouldRejectCollectedItemsFromAnotherStt() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();

        RetrievedStockItem foreign = collectedItem("rsi-1", RetrievedStockItemStatus.COLLECTED_BY_STT);
        foreign.setSttId("stt-2");
        foreign.setCollectedBySttId("stt-2");

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));
        when(userRepository.findById("zm-1")).thenReturn(Optional.of(zm()));
        when(retrievedStockItemRepository.findAllById(List.of("rsi-1"))).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.createReturn(baseCollectedRequest("rsi-1").build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to your STT collected stock");
    }

    @Test
    void createReturn_shouldRejectPendingCollectedItems() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();

        RetrievedStockItem pending = collectedItem("rsi-1", RetrievedStockItemStatus.PENDING_STT_COLLECTION);

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));
        when(userRepository.findById("zm-1")).thenReturn(Optional.of(zm()));
        when(retrievedStockItemRepository.findAllById(List.of("rsi-1"))).thenReturn(List.of(pending));

        assertThatThrownBy(() -> service.createReturn(baseCollectedRequest("rsi-1").build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("is not selectable for a return");
    }

    @Test
    void createReturn_shouldReserveCollectedItemsAndLeaveNormalStockUntouched() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();

        RetrievedStockItem first = collectedItem("rsi-1", RetrievedStockItemStatus.COLLECTED_BY_STT);
        RetrievedStockItem second = collectedItem("rsi-2", RetrievedStockItemStatus.COLLECTED_BY_STT);

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));
        when(userRepository.findById("zm-1")).thenReturn(Optional.of(zm()));
        when(dpmRepository.findBySttIdAndStatusInOrderByCreatedAtDesc(any(), anyList())).thenReturn(List.of());
        when(retrievedStockItemRepository.findAllById(List.of("rsi-1", "rsi-2"))).thenReturn(List.of(first, second));
        when(equipmentRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(returnRepository.save(any(MaterialReturnRequest.class))).thenAnswer(invocation -> {
            MaterialReturnRequest request = invocation.getArgument(0);
            request.setId("return-1");
            return request;
        });

        MaterialReturnResponseDto response = service.createReturn(baseCollectedRequest("rsi-1", "rsi-2").build());

        assertThat(response.getReturnSource()).isEqualTo(ReturnSource.COLLECTED_ITEM_STOCK);
        assertThat(response.getItems()).hasSize(2);
        assertThat(first.getStatus()).isEqualTo(RetrievedStockItemStatus.IN_RETURN_REQUEST);
        assertThat(second.getStatus()).isEqualTo(RetrievedStockItemStatus.IN_RETURN_REQUEST);
        assertThat(first.getMaterialReturnRequestId()).isEqualTo("return-1");
        assertThat(second.getMaterialReturnRequestId()).isEqualTo("return-1");
        verify(retrievedStockItemRepository).saveAll(List.of(first, second));
        verify(sttStockRepository, never()).save(any(SttStock.class));
        verify(notificationEventService).onMaterialReturnSubmitted(any());
    }

    @Test
    void createReturn_shouldRollbackCollectedReservationsWhenTrackedReservationFails() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();

        RetrievedStockItem tracked = collectedItem("rsi-1", RetrievedStockItemStatus.COLLECTED_BY_STT);
        tracked.setInstanceId("inst-1");

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));
        when(userRepository.findById("zm-1")).thenReturn(Optional.of(zm()));
        when(dpmRepository.findBySttIdAndStatusInOrderByCreatedAtDesc(any(), anyList())).thenReturn(List.of());
        when(retrievedStockItemRepository.findAllById(List.of("rsi-1"))).thenReturn(List.of(tracked));
        when(retrievedStockItemRepository.findByMaterialReturnRequestId("return-1")).thenReturn(List.of(tracked));
        when(equipmentRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(returnRepository.save(any(MaterialReturnRequest.class))).thenAnswer(invocation -> {
            MaterialReturnRequest request = invocation.getArgument(0);
            request.setId("return-1");
            return request;
        });
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "reserve failed"))
                .when(equipmentInstanceService)
                .reserveForReturn("return-1", "RET-2026-0001", List.of("inst-1"), "stt@example.com");

        assertThatThrownBy(() -> service.createReturn(baseCollectedRequest("rsi-1").build()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("reserve failed");

        assertThat(tracked.getStatus()).isEqualTo(RetrievedStockItemStatus.COLLECTED_BY_STT);
        assertThat(tracked.getMaterialReturnRequestId()).isNull();
        verify(returnRepository).deleteById("return-1");
    }

    @Test
    void respondToSchedule_shouldNotDecrementSttStockForCollectedSource() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();

        MaterialReturnRequest request = collectedReturn(MaterialReturnStatus.SCHEDULE_PROPOSED);

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));
        when(returnRepository.findById("return-1")).thenReturn(Optional.of(request));
        when(returnRepository.save(any(MaterialReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MaterialReturnResponseDto response = service.respondToSchedule("return-1", new SttScheduleResponseDto(true, "ok"));

        assertThat(response.getStatus()).isEqualTo(MaterialReturnStatus.SCHEDULE_CONFIRMED);
        verify(sttStockRepository, never()).findBySttId(any());
        verify(sttStockRepository, never()).save(any(SttStock.class));
    }

    @Test
    void rejectReturn_shouldReleaseCollectedItemsForSelectionAgain() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        MaterialReturnService service = newService();

        MaterialReturnRequest request = collectedReturn(MaterialReturnStatus.PENDING_ZM_REVIEW);
        RetrievedStockItem reserved = collectedItem("rsi-1", RetrievedStockItemStatus.IN_RETURN_REQUEST);
        reserved.setMaterialReturnRequestId("return-1");
        reserved.setAddedToReturnAt(Instant.parse("2026-07-23T09:00:00Z"));

        when(userRepository.findByEmail("zm@example.com")).thenReturn(Optional.of(zm()));
        when(returnRepository.findById("return-1")).thenReturn(Optional.of(request));
        when(returnRepository.save(any(MaterialReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(retrievedStockItemRepository.findByMaterialReturnRequestId("return-1")).thenReturn(List.of(reserved));

        MaterialReturnResponseDto response = service.rejectReturn("return-1", new ReviewMaterialReturnDto("invalid batch"));

        assertThat(response.getStatus()).isEqualTo(MaterialReturnStatus.REJECTED);
        assertThat(reserved.getStatus()).isEqualTo(RetrievedStockItemStatus.COLLECTED_BY_STT);
        assertThat(reserved.getMaterialReturnRequestId()).isNull();
        assertThat(reserved.getAddedToReturnAt()).isNull();
        verify(equipmentInstanceService).releaseReturnReservation("return-1", "zm@example.com");
    }

    @Test
    void markHandedOver_shouldMarkCollectedItemsReturned() {
        authenticateAs("zm@example.com", "ROLE_ZM");
        MaterialReturnService service = newService();

        MaterialReturnRequest request = collectedReturn(MaterialReturnStatus.SCHEDULE_CONFIRMED);
        RetrievedStockItem reserved = collectedItem("rsi-1", RetrievedStockItemStatus.IN_RETURN_REQUEST);
        reserved.setMaterialReturnRequestId("return-1");

        when(userRepository.findByEmail("zm@example.com")).thenReturn(Optional.of(zm()));
        when(returnRepository.findById("return-1")).thenReturn(Optional.of(request));
        when(returnRepository.save(any(MaterialReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(retrievedStockItemRepository.findByMaterialReturnRequestId("return-1")).thenReturn(List.of(reserved));

        MaterialReturnResponseDto response = service.markHandedOver("return-1", new MarkHandedOverDto());

        assertThat(response.getStatus()).isEqualTo(MaterialReturnStatus.HANDED_OVER);
        assertThat(reserved.getStatus()).isEqualTo(RetrievedStockItemStatus.RETURNED);
        assertThat(reserved.getReturnedAt()).isNotNull();
        verify(equipmentInstanceService).markReturnedToDepot("return-1", "zm@example.com");
    }

    @Test
    void createReturn_shouldSucceedAndNotifyZmWhenStockIsSufficient() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));
        SttStock stock = SttStock.builder()
                .sttId("stt-1")
                .items(List.of(SttStockItem.builder()
                        .equipmentId("eq-1").equipmentName("ONT Box").unit("PCS")
                        .totalReceived(10).totalDistributedToTechs(0).build()))
                .build();
        when(sttStockRepository.findBySttId("stt-1")).thenReturn(Optional.of(stock));
        when(userRepository.findById("zm-1")).thenReturn(Optional.of(zm()));
        when(dpmRepository.findBySttIdAndStatusInOrderByCreatedAtDesc(any(), anyList())).thenReturn(List.of());
        when(equipmentRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(returnRepository.save(any(MaterialReturnRequest.class))).thenAnswer(invocation -> {
            MaterialReturnRequest request = invocation.getArgument(0);
            request.setId("return-1");
            return request;
        });

        MaterialReturnResponseDto result = service.createReturn(baseSttStockRequest().build());

        assertThat(result).isNotNull();
        verify(notificationEventService).onMaterialReturnSubmitted(any());
        verify(stockMovementService).recordMaterialReturn(any(), eq("stt@example.com"));
    }
}
