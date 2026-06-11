package com.pfe.case_management.dto;

import com.pfe.case_management.entity.CaseEntity;

import java.util.List;

/**
 * One page of cases for a board. {@code items} are lean (the heavy {@code timeline} and
 * {@code workProofs} arrays are excluded server-side — they are only needed in the detail modal,
 * which is fetched on demand via {@code GET /api/v1/cases/{id}}).
 */
public record CasePageResponse(
        List<CaseEntity> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
