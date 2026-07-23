package com.pfe.stock_management.service;

import com.pfe.MainPackage.Dto.NotificationMessageDto;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.Notification;
import com.pfe.MainPackage.Repository.NotificationRepository;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationService;
import com.pfe.MainPackage.Service.SttZmCoverageService;
import com.pfe.Notification.WebSocketNotificationSender;
import com.pfe.stock_management.entity.SttStock;
import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.model.SttStockItem;
import com.pfe.stock_management.model.TechStockItem;
import com.pfe.stock_management.repository.StockThresholdRepository;
import com.pfe.stock_management.repository.SttStockRepository;
import com.pfe.stock_management.repository.TechStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates that stock alerts are generated for the ZM resolved from coverage
 * ({@link SttZmCoverageService}) — not the removed {@code assignedZm} link — and that
 * the STT is notified for TECH stock, unrelated ZMs are excluded, and alerts resolve
 * when stock recovers.
 */
@ExtendWith(MockitoExtension.class)
class StockAlertEvaluationServiceTest {

    @Mock private StockThresholdRepository thresholdRepository;
    @Mock private SttStockRepository sttStockRepository;
    @Mock private TechStockRepository techStockRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private WebSocketNotificationSender wsNotificationSender;
    @Mock private SttZmCoverageService sttZmCoverageService;

    private StockAlertEvaluationService newService() {
        // Every generated alert is "new" (no active duplicate) and persists successfully.
        lenient().when(notificationRepository
                .existsByRecipientEmailAndEventTypeAndEntityIdAndReadFalse(anyString(), anyString(), anyString()))
                .thenReturn(false);
        lenient().when(notificationService.createForRecipient(anyString(), any()))
                .thenReturn(Notification.builder().build());
        lenient().when(thresholdRepository.findByZmIdAndEnabledTrue(anyString())).thenReturn(List.of());
        return new StockAlertEvaluationService(
                thresholdRepository, sttStockRepository, techStockRepository, userRepository,
                notificationService, notificationRepository, wsNotificationSender, sttZmCoverageService);
    }

    private AppUser zm(String id, String email) {
        return AppUser.builder().id(id).fullName("ZM " + id).email(email)
                .coveredGovernorates(List.of("Tunis")).build();
    }

    private AppUser stt(String id, String email) {
        return AppUser.builder().id(id).fullName("STT " + id).email(email).build();
    }

    // Scenario 1 & 3: TECH stock in warning → responsible ZM (and STT) receive a WARNING alert.
    @Test
    void techWarning_notifiesSttAndResponsibleZm() {
        AppUser zm = zm("zm1", "zm1@x.com");
        AppUser stt = stt("stt1", "stt1@x.com");
        AppUser tech = AppUser.builder().id("t1").fullName("Tech 1").assignedSttId("stt1").build();

        when(userRepository.findById("t1")).thenReturn(Optional.of(tech));
        when(userRepository.findById("stt1")).thenReturn(Optional.of(stt));
        when(sttZmCoverageService.findApprovingZmsForStt(stt)).thenReturn(List.of(zm));
        when(techStockRepository.findByTechId("t1")).thenReturn(Optional.of(TechStock.builder()
                .techId("t1")
                .items(List.of(TechStockItem.builder()
                        .equipmentId("e1").equipmentName("Cable").unit("PIECES")
                        .receivedQuantity(2).usedQuantity(0).build())) // remaining 2 → warning (<=3, >0)
                .build()));

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationMessageDto> payloadCaptor = ArgumentCaptor.forClass(NotificationMessageDto.class);

        newService().evaluateTechStock("t1");

        verify(notificationService, times(2))
                .createForRecipient(emailCaptor.capture(), payloadCaptor.capture());
        assertThat(emailCaptor.getAllValues()).containsExactlyInAnyOrder("stt1@x.com", "zm1@x.com");
        assertThat(payloadCaptor.getAllValues())
                .allSatisfy(p -> assertThat(p.getLevel()).isEqualTo("WARNING"));
    }

    // Scenario 2: TECH stock in critical → CRITICAL alert to the ZM.
    @Test
    void techCritical_notifiesResponsibleZmWithCriticalLevel() {
        AppUser zm = zm("zm1", "zm1@x.com");
        AppUser stt = stt("stt1", "stt1@x.com");
        AppUser tech = AppUser.builder().id("t1").fullName("Tech 1").assignedSttId("stt1").build();

        when(userRepository.findById("t1")).thenReturn(Optional.of(tech));
        when(userRepository.findById("stt1")).thenReturn(Optional.of(stt));
        when(sttZmCoverageService.findApprovingZmsForStt(stt)).thenReturn(List.of(zm));
        when(techStockRepository.findByTechId("t1")).thenReturn(Optional.of(TechStock.builder()
                .techId("t1")
                .items(List.of(TechStockItem.builder()
                        .equipmentId("e1").equipmentName("Cable").unit("PIECES")
                        .receivedQuantity(0).usedQuantity(0).build())) // remaining 0 → critical (<=0)
                .build()));

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationMessageDto> payloadCaptor = ArgumentCaptor.forClass(NotificationMessageDto.class);

        newService().evaluateTechStock("t1");

        verify(notificationService, times(2))
                .createForRecipient(emailCaptor.capture(), payloadCaptor.capture());
        assertThat(emailCaptor.getAllValues()).contains("zm1@x.com");
        assertThat(payloadCaptor.getAllValues())
                .allSatisfy(p -> assertThat(p.getLevel()).isEqualTo("CRITICAL"));
    }

    // Scenario 4: STT stock in critical → CRITICAL alert to its ZM.
    @Test
    void sttCritical_notifiesResponsibleZm() {
        AppUser zm = zm("zm1", "zm1@x.com");
        AppUser stt = stt("stt1", "stt1@x.com");

        when(userRepository.findById("stt1")).thenReturn(Optional.of(stt));
        when(sttZmCoverageService.findApprovingZmsForStt(stt)).thenReturn(List.of(zm));
        when(sttStockRepository.findBySttId("stt1")).thenReturn(Optional.of(SttStock.builder()
                .sttId("stt1")
                .items(List.of(SttStockItem.builder()
                        .equipmentId("e1").equipmentName("Cable").unit("PIECES")
                        .totalReceived(0).build())) // available 0 → critical
                .build()));

        ArgumentCaptor<NotificationMessageDto> payloadCaptor = ArgumentCaptor.forClass(NotificationMessageDto.class);

        newService().evaluateSttStock("stt1");

        verify(notificationService).createForRecipient(eq("zm1@x.com"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getLevel()).isEqualTo("CRITICAL");
        verify(wsNotificationSender).sendToUser(eq("zm1@x.com"), any());
    }

    // Scenario 5: an STT with no responsible ZM (coverage) generates no ZM alert.
    @Test
    void sttWithoutResponsibleZm_generatesNoAlert() {
        AppUser stt = stt("stt1", "stt1@x.com");
        when(userRepository.findById("stt1")).thenReturn(Optional.of(stt));
        when(sttZmCoverageService.findApprovingZmsForStt(stt)).thenReturn(List.of());

        newService().evaluateSttStock("stt1");

        verify(notificationService, never()).createForRecipient(anyString(), any());
    }

    // Scenario 6: stock recovers above threshold → the active alert is resolved (read + RESOLVED).
    @Test
    void sttRecovered_resolvesActiveAlert() {
        AppUser zm = zm("zm1", "zm1@x.com");
        AppUser stt = stt("stt1", "stt1@x.com");

        when(userRepository.findById("stt1")).thenReturn(Optional.of(stt));
        when(sttZmCoverageService.findApprovingZmsForStt(stt)).thenReturn(List.of(zm));
        when(sttStockRepository.findBySttId("stt1")).thenReturn(Optional.of(SttStock.builder()
                .sttId("stt1")
                .items(List.of(SttStockItem.builder()
                        .equipmentId("e1").equipmentName("Cable").unit("PIECES")
                        .totalReceived(50).build())) // available 50 → normal
                .build()));

        Notification active = Notification.builder().read(false).status("ACTIVE").build();
        when(notificationRepository.findByRecipientEmailAndEntityIdAndReadFalse(
                eq("zm1@x.com"), eq("STT:stt1:e1"))).thenReturn(List.of(active));

        newService().evaluateSttStock("stt1");

        verify(notificationService, never()).createForRecipient(anyString(), any());
        assertThat(active.isRead()).isTrue();
        assertThat(active.getStatus()).isEqualTo("RESOLVED");
        verify(notificationRepository).saveAll(any());
    }
}
