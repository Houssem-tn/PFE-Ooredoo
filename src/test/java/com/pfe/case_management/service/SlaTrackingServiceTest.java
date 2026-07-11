package com.pfe.case_management.service;

import com.pfe.case_management.entity.CaseEntity;
import com.pfe.case_management.enums.SlaStatus;
import com.pfe.case_management.model.SlaTrackingInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests SLA status computation with explicit Instants passed in — no
 * sleeping. Mirrors application.properties production defaults
 * (app.sla.rdv-window=6h, app.sla.warning-threshold=0.5), set here via
 * ReflectionTestUtils since this is a plain unit test with no Spring context
 * to resolve the @Value placeholders.
 */
class SlaTrackingServiceTest {

    private SlaTrackingService service;

    @BeforeEach
    void setUp() {
        service = new SlaTrackingService();
        ReflectionTestUtils.setField(service, "rdvWindow", Duration.ofHours(6));
        ReflectionTestUtils.setField(service, "warningThreshold", 0.5);
    }

    @Test
    void startRdvSla_shouldInitializeOnTrackWithFullWindowRemaining() {
        CaseEntity c = new CaseEntity();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        service.startRdvSla(c, start);

        SlaTrackingInfo info = c.getSlaTracking();
        assertThat(info.getStatus()).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(info.getStartedAt()).isEqualTo(start);
        assertThat(info.getDeadlineAt()).isEqualTo(start.plus(Duration.ofHours(6)));
    }

    @Test
    void refreshLiveState_shouldStayOnTrackBeforeWarningThreshold() {
        CaseEntity c = new CaseEntity();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.startRdvSla(c, start);

        // 2h of a 6h window elapsed (33%) — well before the 50% warning threshold.
        service.refreshLiveState(c, start.plus(Duration.ofHours(2)));

        assertThat(c.getSlaTracking().getStatus()).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    void refreshLiveState_shouldFlipToWarningPastHalfTheWindow() {
        CaseEntity c = new CaseEntity();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.startRdvSla(c, start);

        // 4h of 6h elapsed (66%) — past the 50% warning threshold, still before deadline.
        boolean changed = service.refreshLiveState(c, start.plus(Duration.ofHours(4)));

        assertThat(changed).isTrue();
        assertThat(c.getSlaTracking().getStatus()).isEqualTo(SlaStatus.WARNING);
        assertThat(c.getSlaTracking().getWarningTriggeredAt()).isNotNull();
    }

    @Test
    void refreshLiveState_shouldFlipToBreachedAtOrAfterDeadline() {
        CaseEntity c = new CaseEntity();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.startRdvSla(c, start);

        boolean changed = service.refreshLiveState(c, start.plus(Duration.ofHours(6)));

        assertThat(changed).isTrue();
        assertThat(c.getSlaTracking().getStatus()).isEqualTo(SlaStatus.BREACHED);
        assertThat(c.getSlaTracking().getBreachedAt()).isNotNull();
    }

    @Test
    void completeOnRdv_shouldMarkCompletedOnTimeWhenBeforeDeadline() {
        CaseEntity c = new CaseEntity();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.startRdvSla(c, start);

        service.completeOnRdv(c, start.plus(Duration.ofHours(3)));

        assertThat(c.getSlaTracking().getStatus()).isEqualTo(SlaStatus.COMPLETED_ON_TIME);
        assertThat(c.getSlaTracking().getResponseDurationSeconds()).isEqualTo(Duration.ofHours(3).getSeconds());
    }

    @Test
    void completeOnRdv_shouldMarkCompletedLateWhenAfterDeadline() {
        CaseEntity c = new CaseEntity();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.startRdvSla(c, start);

        service.completeOnRdv(c, start.plus(Duration.ofHours(7)));

        assertThat(c.getSlaTracking().getStatus()).isEqualTo(SlaStatus.COMPLETED_LATE);
        assertThat(c.getSlaTracking().getBreachedAt()).isNotNull();
    }

    @Test
    void forceBreach_shouldNotOverrideAnAlreadyTerminalStatus() {
        CaseEntity c = new CaseEntity();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        service.startRdvSla(c, start);
        service.completeOnRdv(c, start.plus(Duration.ofHours(1)));

        service.forceBreach(c, start.plus(Duration.ofHours(2)));

        // Once COMPLETED_ON_TIME, forceBreach must be a no-op.
        assertThat(c.getSlaTracking().getStatus()).isEqualTo(SlaStatus.COMPLETED_ON_TIME);
    }
}
