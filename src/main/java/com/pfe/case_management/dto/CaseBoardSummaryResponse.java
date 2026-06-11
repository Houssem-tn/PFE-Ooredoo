package com.pfe.case_management.dto;

import java.util.List;

/**
 * Aggregate counts for a board's stat cards, computed over the WHOLE filtered set (not just the
 * current page), plus the distinct governorate/delegation values that feed the filter dropdowns.
 */
public record CaseBoardSummaryResponse(
        long totalCases,
        long ownedByZm,
        long assignedStt,
        long assignedTech,
        long slaWarning,
        long slaBreached,
        long workDone,
        long closed,
        List<String> governorates,
        List<String> delegations
) {
}
