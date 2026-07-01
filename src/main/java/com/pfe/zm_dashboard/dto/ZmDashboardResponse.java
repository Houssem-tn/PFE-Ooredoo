package com.pfe.zm_dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Full payload for the Zone Manager operational dashboard
 * ({@code GET /api/v1/zm/dashboard}). Every section is scoped to the logged-in ZM:
 * cases where {@code zmEmail} matches the ZM, STTs whose covered governorates overlap
 * the ZM territory, DPMs created by the ZM, returns targeting the ZM, imports uploaded
 * by the ZM and notifications addressed to the ZM. No other ZM's data is ever included.
 */
public record ZmDashboardResponse(
        Kpis kpis,
        List<ModuleStats> casesByModule,
        List<StatusCount> casesByStatus,
        List<SttPerformance> sttPerformance,
        ActionItems actionItems,
        StockOverview stockOverview,
        List<TodoImportStat> todoImportStats,
        List<GovernorateStat> governorateStats,
        List<CompletionPoint> completionTrend,
        List<AlertItem> recentAlerts,
        Instant generatedAt
) {

    /** Headline KPI counters shown as the top card row. */
    public record Kpis(
            long totalCases,
            long pending,
            long assignedStt,
            long assignedTech,
            long inProgress,
            long workDone,
            long waitingValidation,
            long closed,
            long overdueSla,
            long activeStts,
            long openDpms,
            long pendingReturns
    ) {}

    /** Per-module (ACT / PLT / RES) status breakdown. */
    public record ModuleStats(
            String module,
            long total,
            long pending,
            long assignedStt,
            long assignedTech,
            long inProgress,
            long workDone,
            long waitingValidation,
            long closed,
            long overdue
    ) {}

    /** One slice of the "cases by status" donut. */
    public record StatusCount(String status, long count) {}

    /** One row of the STT performance table. Material figures come from the STT stock ledger. */
    public record SttPerformance(
            String sttId,
            String sttName,
            String sttEmail,
            long assigned,
            long completed,
            long pending,
            long late,
            double avgResolutionMinutes,
            double slaRespectPercent,
            long materialReceived,
            long materialDistributed,
            long materialReturned,
            long materialDamagedMissing
    ) {}

    /** "Needs your action" groups — a count plus a short preview list for deep-linking. */
    public record ActionItems(
            ActionGroup waitingAssignment,
            ActionGroup waitingValidation,
            ActionGroup overdue,
            ActionGroup pendingReturns,
            ActionGroup dpmFollowUp,
            ActionGroup missingImeiSerial,
            ActionGroup stockAnomalies
    ) {}

    public record ActionGroup(long count, List<ActionPreview> preview) {}

    /** A single deep-linkable preview row inside an {@link ActionGroup}. */
    public record ActionPreview(String id, String reference, String label, String meta) {}

    /** Aggregated stock & material movement across the ZM territory. */
    public record StockOverview(
            long dpmCreated,
            long dpmConfirmed,
            long materialReceivedByStt,
            long materialDistributed,
            long materialConsumed,
            long materialReturned,
            long recoverable,
            long trackedUnits,
            long missingImeiSerial
    ) {}

    /** A recent TODO import performed by this ZM. */
    public record TodoImportStat(
            String module,
            String fileName,
            Instant importDate,
            long totalRows,
            long createdCases,
            long duplicateRows,
            long invalidRows,
            String status
    ) {}

    /** Workload per governorate — feeds the governorate bar chart and the mini map. */
    public record GovernorateStat(String governorate, long total, long pending, long overdue) {}

    /** One point of the "completed cases over time" line chart (day bucket). */
    public record CompletionPoint(String date, long completed) {}

    /** A recent notification addressed to this ZM. */
    public record AlertItem(
            String title,
            String message,
            String level,
            String category,
            Instant createdAt,
            String relatedCaseId
    ) {}
}
