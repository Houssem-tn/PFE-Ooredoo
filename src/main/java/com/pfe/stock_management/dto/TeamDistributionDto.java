package com.pfe.stock_management.dto;

import com.pfe.stock_management.enums.DistributionStatus;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One row of the team-level distribution tracking view (Admin / ZM). Independent
 * of DPM source: every STT→TECH distribution appears here, including new pooled
 * distributions whose legacy {@code dpmId} is null.
 *
 * <p>Per-item usage / remaining is derived from the TECH's aggregated stock via a
 * FIFO drawdown across that TECH's confirmed distributions, so the numbers always
 * reconcile with {@code TechStock}. The DPM source breakdown (when available) is
 * attached per item.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamDistributionDto {

    private String distributionId;

    private String sttId;
    private String sttName;
    private String techId;
    private String techName;
    private String zmId;
    private String zmName;

    private Instant distributedAt;
    private Instant confirmedAt;
    private String notes;

    /** Raw persisted status. */
    private DistributionStatus status;

    /** Derived lifecycle status: PENDING | CONFIRMED | PARTIALLY_USED | FULLY_USED | CANCELLED. */
    private String derivedStatus;

    /** True when this is a pre-upgrade pooled distribution with no DPM source available. */
    private boolean historical;

    @Builder.Default
    private List<Item> items = new ArrayList<>();

    private int totalDistributed;
    private int totalUsed;
    private int totalRemaining;

    @Builder.Default
    private List<RelatedCase> relatedCases = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String equipmentId;
        private String equipmentName;
        private String unit;
        private int distributedQuantity;
        private int usedQuantity;
        private int remainingQuantity;

        @Builder.Default
        private List<SourceAllocation> sources = new ArrayList<>();
    }

    /** Compact DPM source breakdown for one distributed item. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SourceAllocation {
        private String dpmId;
        private String dpmNumber;
        private int allocatedQuantity;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RelatedCase {
        private String caseId;
        private String crmCase;
        private String caseType;
        private Instant consumedAt;
    }
}
