package com.pfe.stock_management.dto;

import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DpmTrackingDto {
    private DpmRequest dpm;

    /** Legacy: distributions that carry this DPM id directly (pre-upgrade flow). */
    @Builder.Default
    private List<SttDistribution> techDistributions = new ArrayList<>();

    /** New: automatic source allocations attributing pooled distributions to this DPM. */
    @Builder.Default
    private List<DpmAllocationDto> allocations = new ArrayList<>();

    /** New: per-TECH distributed/used/returned/remaining rollup built from allocations. */
    @Builder.Default
    private List<DpmTechRollupDto> techRollups = new ArrayList<>();
}
