package com.pfe.stock_management.retrieved_stock;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** ZM payload confirming physical collection of one or more pending retrieved-stock items. */
public record ConfirmCollectionRequest(
        @NotEmpty(message = "Select at least one item to confirm collection")
        List<String> itemIds,
        String note
) {
}
