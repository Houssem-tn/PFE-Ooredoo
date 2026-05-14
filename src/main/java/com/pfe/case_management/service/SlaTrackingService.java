package com.pfe.case_management.service;

import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.CaseStatus;
import com.pfe.case_management.enums.ExecutionMode;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.SlaTrackingInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Service
public class SlaTrackingService {

    private static final Set<SlaStatus> TERMINAL_SLA_STATUSES = EnumSet.of(
            SlaStatus.COMPLETED_ON_TIME,
            SlaStatus.COMPLETED_LATE
    );

    @Value("${app.sla.rdv-window:6h}")
    private Duration rdvWindow;

    @Value("${app.sla.warning-threshold:0.5}")
    private double warningThreshold;

    public void startRdvSla(CaseEntity caseEntity, Instant startAt) {
        if (caseEntity == null) {
            return;
        }

        Instant effectiveStart = startAt != null ? startAt : Instant.now();
        Instant deadline = effectiveStart.plus(resolveWindow());

        SlaTrackingInfo info = SlaTrackingInfo.builder()
                .startedAt(effectiveStart)
                .deadlineAt(deadline)
                .status(SlaStatus.ON_TRACK)
                .progressPercent(100d)
                .warningThresholdPercent(warningRemainingPercent())
                .remainingSeconds(Math.max(0L, Duration.between(effectiveStart, deadline).getSeconds()))
                .responseDurationSeconds(null)
                .warningNotificationSent(false)
                .breachNotificationSent(false)
                .escalationNotificationSent(false)
                .build();

        caseEntity.setSlaTracking(info);
    }

    public boolean refreshLiveState(CaseEntity caseEntity, Instant now) {
        if (caseEntity == null) {
            return false;
        }

        Instant referenceNow = now != null ? now : Instant.now();

        boolean initialized = ensureInitializedForLegacy(caseEntity, referenceNow);

        SlaTrackingInfo info = caseEntity.getSlaTracking();
        if (info == null || info.getStartedAt() == null || info.getDeadlineAt() == null) {
            return initialized;
        }

        if (info.getWarningThresholdPercent() == null) {
            info.setWarningThresholdPercent(warningRemainingPercent());
        }

        if (info.getRdvSetAt() != null || isTerminal(info.getStatus())) {
            // Keep terminal values stable and predictable.
            Double previousProgress = info.getProgressPercent();
            Long previousRemaining = info.getRemainingSeconds();
            updateProgressAndRemaining(info, info.getRdvSetAt() != null ? info.getRdvSetAt() : referenceNow);
            return initialized
                    || !Objects.equals(previousProgress, info.getProgressPercent())
                    || !Objects.equals(previousRemaining, info.getRemainingSeconds());
        }

        updateProgressAndRemaining(info, referenceNow);

        SlaStatus previous = info.getStatus();
        SlaStatus next = computeCurrentStatus(info, referenceNow);
        info.setStatus(next);

        if (next == SlaStatus.WARNING && info.getWarningTriggeredAt() == null) {
            info.setWarningTriggeredAt(referenceNow);
        }

        if (next == SlaStatus.BREACHED && info.getBreachedAt() == null) {
            info.setBreachedAt(referenceNow);
        }

        return initialized || previous != next;
    }

    public void completeOnRdv(CaseEntity caseEntity, Instant rdvSetAt) {
        if (caseEntity == null) {
            return;
        }

        Instant effectiveRdvSetAt = rdvSetAt != null ? rdvSetAt : Instant.now();
        ensureInitializedForLegacy(caseEntity, effectiveRdvSetAt);

        SlaTrackingInfo info = caseEntity.getSlaTracking();
        if (info == null || info.getStartedAt() == null || info.getDeadlineAt() == null) {
            return;
        }

        info.setRdvSetAt(effectiveRdvSetAt);

        long responseSeconds = Math.max(0L, Duration.between(info.getStartedAt(), effectiveRdvSetAt).getSeconds());
        info.setResponseDurationSeconds(responseSeconds);

        boolean late = effectiveRdvSetAt.isAfter(info.getDeadlineAt());
        info.setStatus(late ? SlaStatus.COMPLETED_LATE : SlaStatus.COMPLETED_ON_TIME);

        if (late && info.getBreachedAt() == null) {
            info.setBreachedAt(effectiveRdvSetAt);
        }

        updateProgressAndRemaining(info, effectiveRdvSetAt);
    }

    public void forceBreach(CaseEntity caseEntity, Instant breachedAt) {
        if (caseEntity == null) {
            return;
        }

        Instant effectiveBreachTime = breachedAt != null ? breachedAt : Instant.now();
        ensureInitializedForLegacy(caseEntity, effectiveBreachTime);

        SlaTrackingInfo info = caseEntity.getSlaTracking();
        if (info == null || isTerminal(info.getStatus())) {
            return;
        }

        info.setStatus(SlaStatus.BREACHED);
        info.setBreachedAt(info.getBreachedAt() != null ? info.getBreachedAt() : effectiveBreachTime);
        updateProgressAndRemaining(info, effectiveBreachTime);
    }

    private boolean ensureInitializedForLegacy(CaseEntity caseEntity, Instant now) {
        if (caseEntity.getSlaTracking() != null && caseEntity.getSlaTracking().getStartedAt() != null) {
            return false;
        }

        if (!isSlaTrackedState(caseEntity)) {
            return false;
        }

        Instant start = resolveSlaStart(caseEntity, now);
        startRdvSla(caseEntity, start);
        return true;
    }

    private boolean isSlaTrackedState(CaseEntity caseEntity) {
        if (caseEntity == null || caseEntity.getStatus() == null) {
            return false;
        }

        if (caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_TECH || caseEntity.getStatus() == CaseStatus.NOT_REACHABLE) {
            return true;
        }

        return caseEntity.getStatus() == CaseStatus.ASSIGNED_TO_STT
                && caseEntity.getExecutionMode() == ExecutionMode.STT_SELF_EXECUTION;
    }

    private Instant resolveSlaStart(CaseEntity caseEntity, Instant fallback) {
        if (caseEntity != null && caseEntity.getExecutionMode() == ExecutionMode.STT_SELF_EXECUTION) {
            return caseEntity.getDateAffectationStt() != null ? caseEntity.getDateAffectationStt() : fallback;
        }

        return caseEntity != null && caseEntity.getDateAffectationTech() != null
                ? caseEntity.getDateAffectationTech()
                : fallback;
    }

    private SlaStatus computeCurrentStatus(SlaTrackingInfo info, Instant now) {
        if (!now.isBefore(info.getDeadlineAt())) {
            return SlaStatus.BREACHED;
        }

        double remainingRatio = remainingRatio(info.getStartedAt(), info.getDeadlineAt(), now);
        double warningRemainingThreshold = (info.getWarningThresholdPercent() != null)
                ? info.getWarningThresholdPercent() / 100d
                : (1d - normalizedThreshold());

        if (remainingRatio <= Math.max(0d, warningRemainingThreshold)) {
            return SlaStatus.WARNING;
        }

        return SlaStatus.ON_TRACK;
    }

    private void updateProgressAndRemaining(SlaTrackingInfo info, Instant referenceTime) {
        double ratio = remainingRatio(info.getStartedAt(), info.getDeadlineAt(), referenceTime);
        double percent = Math.max(0d, Math.min(100d, ratio * 100d));

        long remaining = Math.max(0L, Duration.between(referenceTime, info.getDeadlineAt()).getSeconds());

        info.setProgressPercent(percent);
        info.setRemainingSeconds(remaining);
    }

    private double remainingRatio(Instant startedAt, Instant deadlineAt, Instant referenceTime) {
        long totalSeconds = Math.max(1L, Duration.between(startedAt, deadlineAt).getSeconds());
        long remainingSeconds = Math.max(0L, Duration.between(referenceTime, deadlineAt).getSeconds());
        return (double) remainingSeconds / (double) totalSeconds;
    }

    private Duration resolveWindow() {
        return (rdvWindow == null || rdvWindow.isNegative() || rdvWindow.isZero())
                ? Duration.ofHours(6)
                : rdvWindow;
    }

    private double normalizedThreshold() {
        if (warningThreshold < 0d) {
            return 0d;
        }
        if (warningThreshold > 1d) {
            return 1d;
        }
        return warningThreshold;
    }

    private double warningRemainingPercent() {
        double ratio = Math.max(0d, Math.min(1d, 1d - normalizedThreshold()));
        return ratio * 100d;
    }

    private boolean isTerminal(SlaStatus status) {
        return status != null && TERMINAL_SLA_STATUSES.contains(status);
    }
}
