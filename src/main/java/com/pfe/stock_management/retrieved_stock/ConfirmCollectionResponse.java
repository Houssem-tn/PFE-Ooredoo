package com.pfe.stock_management.retrieved_stock;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Result of a ZM confirm-collection call: the now-{@code COLLECTED} items, for a summary screen. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmCollectionResponse {

    @Builder.Default
    private List<RetrievedStockItemDto> collectedItems = new ArrayList<>();

    private String collectionBatchId;
}
