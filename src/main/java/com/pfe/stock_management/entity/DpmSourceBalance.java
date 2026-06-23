package com.pfe.stock_management.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Internal, per-confirmed-DPM-item source balance. Created when an STT confirms
 * material reception for a DPM. Lets the system attribute pooled STT→TECH
 * distributions back to the DPM(s) that physically sourced them, without forcing
 * the STT to pick a DPM manually.
 *
 * <p>{@code remainingQuantity} is stored explicitly so that allocation can use an
 * atomic, conditional {@code $inc} (decrement only when {@code remaining >= take}),
 * guaranteeing a DPM source quantity can never be allocated twice.</p>
 */
@Document(collection = "stock_dpm_source_balances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpmSourceBalance {

    @Id
    private String id;

    private String dpmId;
    private String dpmNumber;

    private String sttId;
    private String zmId;
    private String zmName;

    private String equipmentId;
    private String equipmentName;
    private String unit;

    /** Quantity the STT confirmed receiving for this DPM item. */
    @Builder.Default
    private int confirmedQuantity = 0;

    /** Quantity already allocated to TECH distributions from this source. */
    @Builder.Default
    private int allocatedQuantity = 0;

    /** confirmedQuantity - allocatedQuantity. Maintained atomically for safe allocation. */
    @Builder.Default
    private int remainingQuantity = 0;

    /** DPM confirmation date — the FIFO ordering key for allocation. */
    private Instant confirmedAt;

    private Instant createdAt;
    private Instant lastAllocatedAt;
}
