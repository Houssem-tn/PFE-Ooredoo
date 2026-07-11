package com.pfe.case_management.service;

import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.case_management.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests SlaSchedulerService.monitorRdvSla() using data manipulated into the
 * past/future relative to the real Instant.now() at test-run time — no
 * Thread.sleep. The scheduler itself calls Instant.now() directly (not
 * clock-injectable), so timestamps are deliberately built with a safety
 * margin (minutes, not milliseconds) around the thresholds.
 */
@ExtendWith(MockitoExtension.class)
class SlaSchedulerServiceTest {

    @Mock
    private CaseRepository caseRepository;
    @Mock
    private NotificationEventService notificationEventService;
    @Mock
    private CaseTimelineService caseTimelineService;

    private SlaTrackingService slaTrackingService;
    private SlaSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        slaTrackingService = new SlaTrackingService();
        ReflectionTestUtils.setField(slaTrackingService, "rdvWindow", Duration.ofHours(6));
        ReflectionTestUtils.setField(slaTrackingService, "warningThreshold", 0.5);

        scheduler = new SlaSchedulerService(caseRepository, slaTrackingService, notificationEventService, caseTimelineService);
        ReflectionTestUtils.setField(scheduler, "escalationThresholdMinutes", 30L);
    }

    private CaseEntity caseWithSla(SlaTrackingInfo info) {
        CaseEntity c = CaseEntity.builder()
                .id("case-1")
                .type(CaseType.ACTIVATION)
                .status(CaseStatus.ASSIGNED_TO_TECH)
                .build();
        c.setSlaTracking(info);
        return c;
    }

    private void stubCandidates(CaseEntity... cases) {
        when(caseRepository.findByTypeAndStatusIn(eq(CaseType.ACTIVATION), any()))
                .thenReturn(List.of(cases));
        when(caseRepository.findByTypeAndStatusIn(eq(CaseType.PLAINTE), any()))
                .thenReturn(List.of());
    }

    @Test
    void monitorRdvSla_shouldFireWarningNotificationOnceWhenThresholdCrossed() {
        Instant now = Instant.now();
        SlaTrackingInfo info = SlaTrackingInfo.builder()
                .startedAt(now.minus(Duration.ofHours(4)))
                .deadlineAt(now.plus(Duration.ofHours(2)))   // 4h/6h elapsed -> WARNING
                .status(SlaStatus.ON_TRACK)
                .warningNotificationSent(false)
                .breachNotificationSent(false)
                .escalationNotificationSent(false)
                .build();
        CaseEntity c = caseWithSla(info);
        stubCandidates(c);

        scheduler.monitorRdvSla();

        verify(notificationEventService, times(1)).onSlaWarning(c);
        verify(notificationEventService, never()).onSlaBreached(any());
        verify(caseRepository).saveAll(List.of(c));
    }

    @Test
    void monitorRdvSla_shouldNotRefireWarningWhenAlreadySent() {
        Instant now = Instant.now();
        SlaTrackingInfo info = SlaTrackingInfo.builder()
                .startedAt(now.minus(Duration.ofHours(4)))
                .deadlineAt(now.plus(Duration.ofHours(2)))
                .status(SlaStatus.WARNING)
                .warningNotificationSent(true)
                .breachNotificationSent(false)
                .escalationNotificationSent(false)
                .build();
        CaseEntity c = caseWithSla(info);
        stubCandidates(c);

        scheduler.monitorRdvSla();

        verify(notificationEventService, never()).onSlaWarning(any());
    }

    @Test
    void monitorRdvSla_shouldFireBreachNotificationOnceWhenDeadlinePassed() {
        Instant now = Instant.now();
        SlaTrackingInfo info = SlaTrackingInfo.builder()
                .startedAt(now.minus(Duration.ofHours(7)))
                .deadlineAt(now.minus(Duration.ofMinutes(5)))  // deadline already passed
                .status(SlaStatus.WARNING)
                .warningNotificationSent(true)
                .breachNotificationSent(false)
                .escalationNotificationSent(false)
                .build();
        CaseEntity c = caseWithSla(info);
        stubCandidates(c);

        scheduler.monitorRdvSla();

        verify(notificationEventService, times(1)).onSlaBreached(c);
    }

    @Test
    void monitorRdvSla_shouldEscalateOnceBreachExceedsThreshold() {
        Instant now = Instant.now();
        // Already BREACHED 45 minutes ago — past the 30-minute escalation threshold.
        SlaTrackingInfo info = SlaTrackingInfo.builder()
                .startedAt(now.minus(Duration.ofHours(8)))
                .deadlineAt(now.minus(Duration.ofMinutes(45)))
                .status(SlaStatus.BREACHED)
                .breachedAt(now.minus(Duration.ofMinutes(45)))
                .warningNotificationSent(true)
                .breachNotificationSent(true)
                .escalationNotificationSent(false)
                .build();
        CaseEntity c = caseWithSla(info);
        stubCandidates(c);

        scheduler.monitorRdvSla();

        verify(notificationEventService, times(1)).onSlaEscalated(any(), anyLong());
        verify(notificationEventService, never()).onSlaBreached(any());
    }

    @Test
    void monitorRdvSla_shouldNotReEscalateWhenAlreadyEscalated() {
        Instant now = Instant.now();
        SlaTrackingInfo info = SlaTrackingInfo.builder()
                .startedAt(now.minus(Duration.ofHours(8)))
                .deadlineAt(now.minus(Duration.ofMinutes(45)))
                .status(SlaStatus.BREACHED)
                .breachedAt(now.minus(Duration.ofMinutes(45)))
                .warningNotificationSent(true)
                .breachNotificationSent(true)
                .escalationNotificationSent(true)
                .build();
        CaseEntity c = caseWithSla(info);
        stubCandidates(c);

        scheduler.monitorRdvSla();

        verify(notificationEventService, never()).onSlaEscalated(any(), anyLong());
        verify(caseRepository, never()).saveAll(any());
    }

    @Test
    void monitorRdvSla_shouldDoNothingWhenNoCandidateCasesExist() {
        when(caseRepository.findByTypeAndStatusIn(eq(CaseType.ACTIVATION), any())).thenReturn(List.of());
        when(caseRepository.findByTypeAndStatusIn(eq(CaseType.PLAINTE), any())).thenReturn(List.of());

        scheduler.monitorRdvSla();

        verify(notificationEventService, never()).onSlaWarning(any());
        verify(notificationEventService, never()).onSlaBreached(any());
        verify(caseRepository, never()).saveAll(any());
    }
}
