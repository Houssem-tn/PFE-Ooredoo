package com.pfe.stock_management.retrieved_stock;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** One technician's pending-collection queue, for the ZM collection page (grouped by technician). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianRetrievedStockGroupDto {

    private String technicianId;
    private String technicianName;
    private String technicianEmail;

    @Builder.Default
    private List<RetrievedStockItemDto> items = new ArrayList<>();
}
