package com.pfe.case_management.model;

import com.pfe.case_management.enums.SlaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaTrackingInfo {

    private Instant startedAt;
    private Instant deadlineAt;
    private Instant rdvSetAt;

    private Instant warningTriggeredAt;
    private Instant breachedAt;
    private Instant escalationTriggeredAt;

    private SlaStatus status;

    private Double progressPercent;
    private Double warningThresholdPercent;
    private Long remainingSeconds;
    private Long responseDurationSeconds;

    private boolean warningNotificationSent;
    private boolean breachNotificationSent;
    private boolean escalationNotificationSent;
}