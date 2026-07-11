package com.pfe.stock_management.service;

import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.metrics.BusinessMetrics;
import com.pfe.stock_management.dto.ConfirmDpmRequest;
import com.pfe.stock_management.dto.DpmItemConfirmation;
import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.enums.DpmStatus;
import com.pfe.stock_management.model.DpmItem;
import com.pfe.stock_management.repository.DpmRepository;
import com.pfe.stock_management.repository.EquipmentRepository;
import com.pfe.stock_management.repository.SttDistributionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class DpmServiceTest {

    @Mock
    private DpmRepository dpmRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SttDistributionRepository distributionRepository;
    @Mock
    private SttStockService sttStockService;
    @Mock
    private StockMovementService stockMovementService;
    @Mock
    private NotificationEventService notificationEventService;
    @Mock
    private DpmAllocationService dpmAllocationService;
    @Mock
    private EquipmentInstanceService equipmentInstanceService;
    @Mock
    private BusinessMetrics businessMetrics;

    @AfterEach
    void clearSecurityContext() {
        clear();
    }

    private DpmService newService() {
        return new DpmService(
                dpmRepository, equipmentRepository, userRepository, distributionRepository,
                sttStockService, stockMovementService, notificationEventService,
                dpmAllocationService, equipmentInstanceService, businessMetrics
        );
    }

    private DpmRequest dpmWith(DpmStatus status, String sttId) {
        return DpmRequest.builder()
                .id("dpm-1")
                .sttId(sttId)
                .status(status)
                .items(new java.util.ArrayList<>(List.of(DpmItem.builder()
                        .equipmentId("eq-1")
                        .equipmentName("ONT Box")
                        .unit("PCS")
                        .requestedQuantity(5)
                        .build())))
                .build();
    }

    @Test
    void confirmDpm_shouldRejectWhenCallerIsNotTheAssignedStt() {
        authenticateAs("someone-else@example.com", "ROLE_STT");
        DpmService service = newService();

        when(userRepository.findByEmail("someone-else@example.com"))
                .thenReturn(Optional.of(AppUser.builder().id("stt-OTHER").email("someone-else@example.com").build()));
        when(dpmRepository.findById("dpm-1")).thenReturn(Optional.of(dpmWith(DpmStatus.RDV_CONFIRMED_BY_STT, "stt-1")));

        ConfirmDpmRequest req = new ConfirmDpmRequest();
        req.setItems(List.of());

        assertThatThrownBy(() -> service.confirmDpm("dpm-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not the assigned STT");
    }

    @Test
    void confirmDpm_shouldRejectWhenDpmIsInWrongStatus() {
        authenticateAs("stt@example.com", "ROLE_STT");
        DpmService service = newService();

        when(userRepository.findByEmail("stt@example.com"))
                .thenReturn(Optional.of(AppUser.builder().id("stt-1").email("stt@example.com").build()));
        when(dpmRepository.findById("dpm-1")).thenReturn(Optional.of(dpmWith(DpmStatus.WAITING_TEAM_STOCK_APPROVAL, "stt-1")));

        ConfirmDpmRequest req = new ConfirmDpmRequest();
        req.setItems(List.of());

        assertThatThrownBy(() -> service.confirmDpm("dpm-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("cannot be confirmed in its current status");
    }

    @Test
    void confirmDpm_shouldRejectWhenAnEquipmentLineHasNoConfirmation() {
        authenticateAs("stt@example.com", "ROLE_STT");
        DpmService service = newService();

        when(userRepository.findByEmail("stt@example.com"))
                .thenReturn(Optional.of(AppUser.builder().id("stt-1").email("stt@example.com").build()));
        when(dpmRepository.findById("dpm-1")).thenReturn(Optional.of(dpmWith(DpmStatus.RDV_CONFIRMED_BY_STT, "stt-1")));

        // Request confirms a DIFFERENT equipment id than what's on the DPM — "eq-1" has no confirmation.
        DpmItemConfirmation conf = new DpmItemConfirmation();
        conf.setEquipmentId("eq-OTHER");
        conf.setConfirmedQuantity(1);

        ConfirmDpmRequest req = new ConfirmDpmRequest();
        req.setItems(List.of(conf));

        assertThatThrownBy(() -> service.confirmDpm("dpm-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing confirmation for equipment: eq-1");
    }

    @Test
    void confirmDpm_shouldTransitionToMaterialReceivedAndApplyStockOnFullConfirmation() {
        authenticateAs("stt@example.com", "ROLE_STT");
        DpmService service = newService();

        DpmRequest dpm = dpmWith(DpmStatus.RDV_CONFIRMED_BY_STT, "stt-1");
        when(userRepository.findByEmail("stt@example.com"))
                .thenReturn(Optional.of(AppUser.builder().id("stt-1").email("stt@example.com").fullName("STT One").build()));
        when(dpmRepository.findById("dpm-1")).thenReturn(Optional.of(dpm));
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());
        when(dpmRepository.save(any(DpmRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DpmItemConfirmation conf = new DpmItemConfirmation();
        conf.setEquipmentId("eq-1");
        conf.setConfirmedQuantity(5);

        ConfirmDpmRequest req = new ConfirmDpmRequest();
        req.setItems(List.of(conf));

        DpmRequest result = service.confirmDpm("dpm-1", req);

        assertThat(result.getStatus()).isEqualTo(DpmStatus.MATERIAL_RECEIVED);
        assertThat(result.getItems().get(0).getConfirmedQuantity()).isEqualTo(5);
        org.mockito.Mockito.verify(sttStockService).applyDpmConfirmation(result);
        org.mockito.Mockito.verify(stockMovementService).recordDpmConfirmed(result, "stt@example.com");
        org.mockito.Mockito.verify(notificationEventService).onDpmConfirmed(result);
    }

    @Test
    void confirmDpm_shouldMarkPartiallyConfirmedOnShortageInLegacyFlow() {
        authenticateAs("stt@example.com", "ROLE_STT");
        DpmService service = newService();

        DpmRequest dpm = dpmWith(DpmStatus.PENDING_STT_CONFIRMATION, "stt-1"); // requestedQuantity=5
        when(userRepository.findByEmail("stt@example.com"))
                .thenReturn(Optional.of(AppUser.builder().id("stt-1").email("stt@example.com").fullName("STT One").build()));
        when(dpmRepository.findById("dpm-1")).thenReturn(Optional.of(dpm));
        when(equipmentRepository.findAllById(any())).thenReturn(List.of());
        when(dpmRepository.save(any(DpmRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DpmItemConfirmation conf = new DpmItemConfirmation();
        conf.setEquipmentId("eq-1");
        conf.setConfirmedQuantity(3); // less than requested (5) -> shortage

        ConfirmDpmRequest req = new ConfirmDpmRequest();
        req.setItems(List.of(conf));

        DpmRequest result = service.confirmDpm("dpm-1", req);

        assertThat(result.getStatus()).isEqualTo(DpmStatus.PARTIALLY_CONFIRMED);
    }
}
