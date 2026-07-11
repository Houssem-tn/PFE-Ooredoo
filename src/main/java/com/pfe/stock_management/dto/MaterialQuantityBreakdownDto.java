package com.pfe.stock_management.dto;

public record MaterialQuantityBreakdownDto(
        String equipmentId,
        String equipmentName,
        String unit,
        long quantity
) {
}
