package com.pfe.stock_management.material_return;

/** Lightweight ZM reference returned by GET /api/v1/stock/returns/eligible-zms */
public record EligibleZmDto(String id, String name, String email) {
}
