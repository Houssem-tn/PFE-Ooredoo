package com.pfe.stock_management.dto;

import com.pfe.stock_management.entity.DpmRequest;
import com.pfe.stock_management.entity.SttDistribution;
import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DpmTrackingDto {
    private DpmRequest dpm;
    private List<SttDistribution> techDistributions;
}
