package com.pfe.case_management.service;

import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseTimelineEventType;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.SlaTrackingInfo;
import com.pfe.case_management.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaSchedulerService {

    private final CaseRepository caseRepository;
    private final SlaTrackingService slaTrackingService;
    private final NotificationEventService notificationEventService;
    private final CaseTimelineService caseTimelineService;

    @Value("${app.sla.escalation-threshold-minutes:30}")
    private long escalationThresholdMinutes;

    @Scheduled(fixedDelayString = "${app.sla.scheduler-delay-ms:30000}")
    public void monitorRdvSla() {
        List<CaseEntity> candidates = new ArrayList<>();
        candidates.addAll(caseRepository.findByTypeAndStatusIn(
                CaseType.ACTIVATION,
                List.of(CaseStatus.ASSIGNED_TO_STT, CaseStatus.ASSIGNED_TO_TECH, CaseStatus.NOT_REACHABLE)
        ));
        candidates.addAll(caseRepository.findByTypeAndStatusIn(
                CaseType.PLAINTE,
                List.of(CaseStatus.ASSIGNED_TO_STT, CaseStatus.ASSIGNED_TO_TECH, CaseStatus.NOT_REACHABLE)
        ));

        if (candidates.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<CaseEntity> changedCases = new ArrayList<>();

        for (CaseEntity caseEntity : candidates) {
            boolean changed = slaTrackingService.refreshLiveState(caseEntity, now);
            SlaTrackingInfo info = caseEntity.getSlaTracking();

            if (info == null) {
                continue;
            }

            if (info.getStatus() == SlaStatus.WARNING && !info.isWarningNotificationSent()) {
                notificationEventService.onSlaWarning(caseEntity);
                info.setWarningNotificationSent(true);
                caseTimelineService.append(
                        caseEntity,
                        CaseTimelineEventType.SLA_WARNING,
                        "SLA warning triggered",
                        "RDV window reached warning threshold",
                        "SYSTEM",
                        caseEntity.getStatus(),
                        caseEntity.getStatus(),
                        Map.of("slaStatus", info.getStatus().name())
                );
                changed = true;
            }

            if (info.getStatus() == SlaStatus.BREACHED && !info.isBreachNotificationSent()) {
                notificationEventService.onSlaBreached(caseEntity);
                info.setBreachNotificationSent(true);
                caseTimelineService.append(
                        caseEntity,
                        CaseTimelineEventType.SLA_BREACHED,
                        "SLA breached",
                        "RDV deadline was missed",
                        "SYSTEM",
                        caseEntity.getStatus(),
                        caseEntity.getStatus(),
                        Map.of("slaStatus", info.getStatus().name())
                );
                changed = true;
            }

            if (shouldEscalate(info, now)) {
                long breachAgeMinutes = Math.max(0L, Duration.between(info.getBreachedAt(), now).toMinutes());
                notificationEventService.onSlaEscalated(caseEntity, breachAgeMinutes);
                info.setEscalationNotificationSent(true);
                info.setEscalationTriggeredAt(now);
                caseTimelineService.append(
                        caseEntity,
                        CaseTimelineEventType.SLA_ESCALATED,
                        "SLA escalation triggered",
                        "Breach exceeded escalation threshold",
                        "SYSTEM",
                        caseEntity.getStatus(),
                        caseEntity.getStatus(),
                        Map.of("breachAgeMinutes", breachAgeMinutes)
                );
                changed = true;
            }

            if (changed) {
                changedCases.add(caseEntity);
            }
        }

        if (!changedCases.isEmpty()) {
            caseRepository.saveAll(changedCases);
            log.debug("SLA scheduler updated {} case(s)", changedCases.size());
        }
    }

    private boolean shouldEscalate(SlaTrackingInfo info, Instant now) {
        if (info == null
                || info.isEscalationNotificationSent()
                || info.getStatus() != SlaStatus.BREACHED
                || info.getBreachedAt() == null) {
            return false;
        }

        long threshold = Math.max(1L, escalationThresholdMinutes);
        long breachAgeMinutes = Math.max(0L, Duration.between(info.getBreachedAt(), now).toMinutes());
        return breachAgeMinutes >= threshold;
    }
}
