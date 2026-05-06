package com.pfe.stock_management.dto;

import com.pfe.stock_management.entity.TechStock;
import com.pfe.stock_management.entity.TechStockUsage;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclareTechUsageResponse {

    private TechStock stock;
    private TechStockUsage usage;

    @Builder.Default
    private boolean hasWarnings = false;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
