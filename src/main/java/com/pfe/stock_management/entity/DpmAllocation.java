package com.pfe.stock_management.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One slice of a single STT→TECH distribution that was automatically allocated
 * back to a confirmed DPM source (FIFO by DPM confirmation date). A distribution
 * can produce several allocations spanning multiple DPMs / equipment lines.
 *
 * <p>This is the bridge that makes DPM source traceability work for the pooled
 * distribution workflow: DPM → STT → (allocation) → TECH distribution.</p>
 */
@Document(collection = "stock_dpm_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmAllocation {

    @Id
    private String id;

    private String distributionId;

    private String dpmId;
    private String dpmNumber;

    private String sttId;
    private String technicianId;
    private String technicianName;

    private String equipmentId;
    private String equipmentName;
    private String unit;

    private int allocatedQuantity;

    /** Source balance snapshot at allocation time (informational). */
    private int sourceBalanceBefore;
    private int sourceBalanceAfter;

    private Instant allocatedAt;
}
