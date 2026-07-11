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
import com.pfe.stock_management.service.EquipmentInstanceService;
import com.pfe.stock_management.service.StockMovementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class MaterialReturnServiceTest {

    @Mock
    private MaterialReturnRepository returnRepository;
    @Mock
    private SttStockRepository sttStockRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DpmRepository dpmRepository;
    @Mock
    private CaseRepository caseRepository;
    @Mock
    private CaseTimelineService caseTimelineService;
    @Mock
    private StockMovementService stockMovementService;
    @Mock
    private NotificationEventService notificationEventService;
    @Mock
    private EquipmentInstanceService equipmentInstanceService;
    @Mock
    private BusinessMetrics businessMetrics;

    @AfterEach
    void clearSecurityContext() {
        clear();
    }

    private MaterialReturnService newService() {
        return new MaterialReturnService(
                returnRepository, sttStockRepository, equipmentRepository, userRepository,
                dpmRepository, caseRepository, caseTimelineService, stockMovementService,
                notificationEventService, equipmentInstanceService, businessMetrics
        );
    }

    private AppUser stt() {
        return AppUser.builder().id("stt-1").email("stt@example.com").fullName("STT One").role(UserRole.ROLE_STT).build();
    }

    private AppUser zm() {
        return AppUser.builder().id("zm-1").email("zm@example.com").fullName("ZM One").role(UserRole.ROLE_ZM).canCreateDpm(true).build();
    }

    private CreateMaterialReturnDto.CreateMaterialReturnDtoBuilder baseRequest() {
        return CreateMaterialReturnDto.builder()
                .responsibleZmId("zm-1")
                .returnSource(ReturnSource.STT_STOCK)
                .reason(ReturnReason.UNUSED)
                .condition(ReturnCondition.GOOD)
                .destination(ReturnDestination.USABLE_STOCK)
                .items(List.of(ReturnedItemDto.builder().equipmentId("eq-1").quantity(2).build()));
    }

    @Test
    void createReturn_shouldRejectOtherReasonWithoutComment() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();
        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));

        CreateMaterialReturnDto req = baseRequest().reason(ReturnReason.OTHER).comment(null).build();

        assertThatThrownBy(() -> service.createReturn(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Comment is required when return reason is OTHER");
    }

    @Test
    void createReturn_shouldRejectWhenSttStockQuantityIsInsufficient() {
        authenticateAs("stt@example.com", "ROLE_STT");
        MaterialReturnService service = newService();

        when(userRepository.findByEmail("stt@example.com")).thenReturn(Optional.of(stt()));
        SttStock stock = SttStock.builder()
                .sttId("stt-1")
                .items(List.of(SttStockItem.builder()
                        .equipmentId("eq-1").equipmentName("ONT Box").unit("PCS")
                        .totalReceived(3).totalDistributedToTechs(0).build()))
                .build();
        when(sttStockRepository.findBySttId("stt-1")).thenReturn(Optional.of(stock));

        // requesting 2 is within the 3 available -> bump to 5 requested to force insufficiency
        CreateMaterialReturnDto req = baseRequest()
                .items(List.of(ReturnedItemDto.builder().equipmentId("eq-1").quantity(5).build()))
                .build();

        assertThatThrownBy(() -> service.createReturn(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient quantity");
    }

    @Test
    void createReturn_shouldRejectWhenSelectedZmCannotReceiveReturns() {
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

        AppUser zmNotAuthorized = AppUser.builder().id("zm-1").email("zm@example.com").role(UserRole.ROLE_ZM).canCreateDpm(false).build();
        when(userRepository.findById("zm-1")).thenReturn(Optional.of(zmNotAuthorized));

        CreateMaterialReturnDto req = baseRequest().build();

        assertThatThrownBy(() -> service.createReturn(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not authorized to receive material returns");
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
        when(dpmRepository.findBySttIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());
        when(returnRepository.save(any(MaterialReturnRequest.class))).thenAnswer(inv -> {
            MaterialReturnRequest r = inv.getArgument(0);
            r.setId("return-1");
            return r;
        });

        CreateMaterialReturnDto req = baseRequest().build();

        MaterialReturnResponseDto result = service.createReturn(req);

        assertThat(result).isNotNull();
        org.mockito.Mockito.verify(notificationEventService).onMaterialReturnSubmitted(any());
        org.mockito.Mockito.verify(stockMovementService).recordMaterialReturn(any(), org.mockito.ArgumentMatchers.eq("stt@example.com"));
    }
}
