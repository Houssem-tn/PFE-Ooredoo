package com.pfe.case_management.dto;

import com.pfe.stock_management.dto.MaterialQuantityBreakdownDto;

import java.util.List;

/**
 * Top summary cards for the Interventions Auditing cockpit.
 *
 * @param totalInterventions total interventions audited
 * @param completedToday      interventions whose work finished today
 * @param slaBreached         interventions currently/closed in SLA breach
 * @param pendingValidation   interventions awaiting ZM validation
 * @param proofMissing        completed interventions with no photo proof
 * @param materialsConsumed   total quantity of material consumed across interventions
 * @param materialsConsumedBreakdown grouped material totals by equipment type and unit
 */
public record InterventionAuditSummaryDto(
        long totalInterventions,
        long completedToday,
        long slaBreached,
        long pendingValidation,
        long proofMissing,
        long materialsConsumed,
        List<MaterialQuantityBreakdownDto> materialsConsumedBreakdown
) {
}
