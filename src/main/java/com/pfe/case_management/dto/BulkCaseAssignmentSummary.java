package com.pfe.case_management.dto;

import com.pfe.MainPackage.Entity.CaseModuleType;
import com.pfe.case_management.enums.CaseType;
import com.pfe.case_management.model.BulkCaseOperationType;

/**
 * Immutable carrier describing a single bulk case-assignment batch that targets one ZM.
 * One {@code BulkCaseAssignmentSummary} produces exactly one aggregated notification, replacing
 * the previous behaviour of emitting one notification per imported/distributed case.
 *
 * @param zmEmail     recipient ZM email (the notification target).
 * @param zmName      recipient ZM full name, used in the human-readable summary.
 * @param caseType    business case type (PLAINTE / ACTIVATION / RESILIATION).
 * @param module      case module scope (PLT / RES), or {@code null} for plain activation cases.
 * @param operation   which bulk operation produced the batch (injection vs distribution).
 * @param count       number of cases assigned to this ZM in this batch (summary count).
 * @param batchId     traceable import/operation batch id for audit and detail navigation.
 * @param triggeredBy actor that triggered the operation (ZM email for self-injection, admin for distribution).
 * @param routeHint   front-end route to the detailed case list for this batch/board.
 */
public record BulkCaseAssignmentSummary(
        String zmEmail,
        String zmName,
        CaseType caseType,
        CaseModuleType module,
        BulkCaseOperationType operation,
        int count,
        String batchId,
        String triggeredBy,
        String routeHint
) {
}
