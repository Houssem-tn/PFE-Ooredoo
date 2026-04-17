package com.pfe.activation_analytics.service;

import com.pfe.activation_analytics.dto.AdminActivationAnalyticsAlertResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsBatchResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsCauseItemResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsDashboardResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsGovernorateItemResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsKpiResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsPipelineStageResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsQualitySliceResponse;
import com.pfe.activation_analytics.dto.AdminActivationAnalyticsSttItemResponse;
import com.pfe.activation_analytics.entity.ActivationAnalyticsImportBatchEntity;
import com.pfe.activation_analytics.entity.ActivationAnalyticsRowEntity;
import com.pfe.activation_analytics.repository.ActivationAnalyticsImportBatchRepository;
import com.pfe.activation_analytics.repository.ActivationAnalyticsRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivationAnalyticsDashboardService {

    private static final int END_TO_END_DELAY_THRESHOLD_DAYS = 5;
    private static final int ASSIGNMENT_DELAY_THRESHOLD_DAYS = 2;
    private static final int RDV_DELAY_THRESHOLD_DAYS = 2;
    private static final int EXECUTION_DELAY_THRESHOLD_DAYS = 3;
    private static final double HIGH_AVERAGE_SLA_THRESHOLD_DAYS = 5d;
    private static final double HIGH_ABANDON_RATE_THRESHOLD = 15d;
    private static final double HIGH_INJOIGNABLE_RATE_THRESHOLD = 15d;
    private static final double HIGH_STT_LOAD_THRESHOLD = 35d;

    private final ActivationAnalyticsRowRepository rowRepository;
    private final ActivationAnalyticsImportBatchRepository batchRepository;

    public AdminActivationAnalyticsDashboardResponse getDashboard(String uploadedBy, String stt, String governorate) {
        List<ActivationAnalyticsImportBatchEntity> batchEntities = batchRepository.findAllByUploadedByOrderByUploadedAtDesc(uploadedBy);
        List<ActivationAnalyticsRowEntity> sourceRows = rowRepository.findByUploadedByAndValidRowTrue(uploadedBy);

        return buildDashboard(
                sourceRows,
                rowRepository.countByUploadedBy(uploadedBy),
                rowRepository.countByUploadedByAndValidRowTrue(uploadedBy),
                batchRepository.countByUploadedBy(uploadedBy),
                batchEntities,
                stt,
                governorate
        );
    }

    public AdminActivationAnalyticsDashboardResponse getSavedStudyDashboard(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        if (batchIds == null || batchIds.isEmpty()) {
            return buildDashboard(List.of(), 0, 0, 0, List.of(), stt, governorate);
        }

        List<ActivationAnalyticsImportBatchEntity> batchEntities =
                batchRepository.findByUploadedByAndBatchIdInOrderByUploadedAtDesc(uploadedBy, batchIds);
        List<ActivationAnalyticsRowEntity> sourceRows =
                rowRepository.findByUploadedByAndBatchIdInAndValidRowTrue(uploadedBy, batchIds);

        return buildDashboard(
                sourceRows,
                sourceRows.size(),
                sourceRows.size(),
                batchEntities.size(),
                batchEntities,
                stt,
                governorate
        );
    }

    private AdminActivationAnalyticsDashboardResponse buildDashboard(
            List<ActivationAnalyticsRowEntity> sourceRows,
            long storedRows,
            long validStoredRows,
            long batchCount,
            List<ActivationAnalyticsImportBatchEntity> batchEntities,
            String stt,
            String governorate
    ) {
        List<String> availableStts = distinctValues(sourceRows, ActivationAnalyticsRowEntity::getNameStt);
        List<String> availableGovernorates = distinctValues(sourceRows, ActivationAnalyticsRowEntity::getGouvernorat);
        String selectedStt = resolveSelection(stt, availableStts);
        String selectedGovernorate = resolveSelection(governorate, availableGovernorates);

        AdminActivationAnalyticsBatchResponse latestBatch = batchEntities.stream()
                .findFirst()
                .map(this::toBatchResponse)
                .orElse(null);
        List<AdminActivationAnalyticsBatchResponse> recentBatches = batchEntities.stream()
                .limit(6)
                .map(this::toBatchResponse)
                .toList();

        List<ActivationAnalyticsRowEntity> rows = sourceRows.stream()
                .filter(row -> matchesSelection(row.getNameSttNormalized(), selectedStt))
                .filter(row -> matchesSelection(row.getGouvernoratNormalized(), selectedGovernorate))
                .toList();

        if (rows.isEmpty()) {
            return emptyDashboard(
                    storedRows,
                    validStoredRows,
                    batchCount,
                    latestBatch,
                    recentBatches,
                    availableStts,
                    availableGovernorates,
                    selectedStt,
                    selectedGovernorate
            );
        }

        int totalCases = rows.size();
        long completedCases = rows.stream().filter(this::isCompleted).count();
        long delayedCases = rows.stream().filter(this::isDelayed).count();
        long blockedCases = rows.stream().filter(this::isBlocked).count();
        long injoignableCases = rows.stream().filter(this::isInjoignable).count();
        long abandonCases = rows.stream().filter(this::isAbandon).count();
        long rdvSuccessCases = rows.stream().filter(this::isRdvSuccess).count();

        List<AdminActivationAnalyticsSttItemResponse> sttRanking = buildSttRanking(rows, totalCases);
        List<AdminActivationAnalyticsGovernorateItemResponse> governorateBreakdown = buildGovernorateBreakdown(rows, totalCases);
        List<AdminActivationAnalyticsCauseItemResponse> topInjoignabiliteCauses =
                buildCauseRanking(rows, ActivationAnalyticsRowEntity::getCauseInjoignabilite);
        List<AdminActivationAnalyticsCauseItemResponse> topAbandonCauses =
                buildCauseRanking(rows, ActivationAnalyticsRowEntity::getCauseAbandon);
        List<AdminActivationAnalyticsPipelineStageResponse> pipelineStages = buildPipelineStages(rows);
        List<AdminActivationAnalyticsQualitySliceResponse> qualityByStt =
                buildQualitySlices(rows, ActivationAnalyticsRowEntity::getNameStt, "Unassigned");
        List<AdminActivationAnalyticsQualitySliceResponse> qualityByGovernorate =
                buildQualitySlices(rows, ActivationAnalyticsRowEntity::getGouvernorat, "Unknown");

        AdminActivationAnalyticsKpiResponse kpis = new AdminActivationAnalyticsKpiResponse(
                totalCases,
                percentage(completedCases, totalCases),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getAssignmentBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getRdvBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getExecutionBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getEndToEndBusinessDays).toList()),
                percentage(delayedCases, totalCases),
                percentage(blockedCases, totalCases),
                percentage(injoignableCases, totalCases),
                percentage(abandonCases, totalCases),
                distinctCount(rows, ActivationAnalyticsRowEntity::getNameStt),
                distinctCount(rows, ActivationAnalyticsRowEntity::getGouvernorat)
        );

        String bestStt = selectBestStt(sttRanking).map(AdminActivationAnalyticsSttItemResponse::sttName).orElse(null);
        String worstStt = selectWorstStt(sttRanking).map(AdminActivationAnalyticsSttItemResponse::sttName).orElse(null);
        String bestGovernorate = selectBestGovernorate(governorateBreakdown)
                .map(AdminActivationAnalyticsGovernorateItemResponse::gouvernorat)
                .orElse(null);
        String worstGovernorate = selectWorstGovernorate(governorateBreakdown)
                .map(AdminActivationAnalyticsGovernorateItemResponse::gouvernorat)
                .orElse(null);
        String mostDelayedStage = pipelineStages.stream()
                .filter(stage -> stage.averageBusinessDays() != null)
                .max(Comparator.comparing(AdminActivationAnalyticsPipelineStageResponse::averageBusinessDays))
                .map(AdminActivationAnalyticsPipelineStageResponse::label)
                .orElse(null);

        Double averageDebitDl = averageDoubles(rows.stream().map(ActivationAnalyticsRowEntity::getDebitDlValue).toList());
        Double averageDebitUl = averageDoubles(rows.stream().map(ActivationAnalyticsRowEntity::getDebitUlValue).toList());
        Double averageRsrp = averageDoubles(rows.stream().map(ActivationAnalyticsRowEntity::getRsrpValue).toList());
        Double averageRsrq = averageDoubles(rows.stream().map(ActivationAnalyticsRowEntity::getRsrqValue).toList());
        Double averageSinr = averageDoubles(rows.stream().map(ActivationAnalyticsRowEntity::getSinrValue).toList());
        Double averageMetrageCable = averageDoubles(rows.stream().map(ActivationAnalyticsRowEntity::getMetrageCableValue).toList());
        Double averageNombreDePv = averageDoubles(rows.stream().map(ActivationAnalyticsRowEntity::getNombreDePvValue).toList());

        List<AdminActivationAnalyticsAlertResponse> alerts =
                buildAlerts(kpis, sttRanking, governorateBreakdown, mostDelayedStage);
        List<String> insights = buildInsights(sttRanking, governorateBreakdown, pipelineStages, latestBatch);

        return new AdminActivationAnalyticsDashboardResponse(
                storedRows,
                validStoredRows,
                batchCount,
                latestBatch,
                recentBatches,
                availableStts,
                availableGovernorates,
                selectedStt,
                selectedGovernorate,
                kpis,
                percentage(rdvSuccessCases, totalCases),
                bestStt,
                worstStt,
                bestGovernorate,
                worstGovernorate,
                mostDelayedStage,
                averageDebitDl,
                averageDebitUl,
                averageRsrp,
                averageRsrq,
                averageSinr,
                averageMetrageCable,
                averageNombreDePv,
                sttRanking,
                governorateBreakdown,
                topInjoignabiliteCauses,
                topAbandonCauses,
                pipelineStages,
                qualityByStt,
                qualityByGovernorate,
                alerts,
                insights
        );
    }

    private AdminActivationAnalyticsDashboardResponse emptyDashboard(
            long storedRows,
            long validStoredRows,
            long batchCount,
            AdminActivationAnalyticsBatchResponse latestBatch,
            List<AdminActivationAnalyticsBatchResponse> recentBatches,
            List<String> availableStts,
            List<String> availableGovernorates,
            String selectedStt,
            String selectedGovernorate
    ) {
        return new AdminActivationAnalyticsDashboardResponse(
                storedRows,
                validStoredRows,
                batchCount,
                latestBatch,
                recentBatches,
                availableStts,
                availableGovernorates,
                selectedStt,
                selectedGovernorate,
                new AdminActivationAnalyticsKpiResponse(0, 0d, null, null, null, null, 0d, 0d, 0d, 0d, 0, 0),
                0d,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private List<String> distinctValues(
            List<ActivationAnalyticsRowEntity> rows,
            Function<ActivationAnalyticsRowEntity, String> extractor
    ) {
        Map<String, String> values = new LinkedHashMap<>();

        for (ActivationAnalyticsRowEntity row : rows) {
            String value = ActivationAnalyticsSupport.cleanString(extractor.apply(row));
            if (!ActivationAnalyticsSupport.hasText(value)) {
                continue;
            }
            values.putIfAbsent(ActivationAnalyticsSupport.normalizeLookupValue(value), value);
        }

        return values.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String resolveSelection(String requestedValue, List<String> availableValues) {
        if (!ActivationAnalyticsSupport.hasText(requestedValue)) {
            return null;
        }

        String normalizedRequested = ActivationAnalyticsSupport.normalizeLookupValue(requestedValue);
        return availableValues.stream()
                .filter(value -> Objects.equals(
                        ActivationAnalyticsSupport.normalizeLookupValue(value),
                        normalizedRequested
                ))
                .findFirst()
                .orElse(requestedValue.trim());
    }

    private boolean matchesSelection(String normalizedRowValue, String selectedValue) {
        if (!ActivationAnalyticsSupport.hasText(selectedValue)) {
            return true;
        }
        return Objects.equals(normalizedRowValue, ActivationAnalyticsSupport.normalizeLookupValue(selectedValue));
    }

    private List<AdminActivationAnalyticsSttItemResponse> buildSttRanking(List<ActivationAnalyticsRowEntity> rows, int totalCases) {
        return groupRows(rows, ActivationAnalyticsRowEntity::getNameStt, "Unassigned").entrySet().stream()
                .map(entry -> toSttItem(entry.getKey(), entry.getValue(), totalCases))
                .sorted(sttPerformanceComparator())
                .toList();
    }

    private AdminActivationAnalyticsSttItemResponse toSttItem(String sttName, List<ActivationAnalyticsRowEntity> rows, int totalCases) {
        int caseCount = rows.size();
        return new AdminActivationAnalyticsSttItemResponse(
                sttName,
                caseCount,
                percentage(caseCount, totalCases),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getAssignmentBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getRdvBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getExecutionBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getEndToEndBusinessDays).toList()),
                percentage(rows.stream().filter(this::isRdvSuccess).count(), caseCount),
                percentage(rows.stream().filter(this::isCompleted).count(), caseCount),
                percentage(rows.stream().filter(this::isAbandon).count(), caseCount),
                percentage(rows.stream().filter(this::isInjoignable).count(), caseCount),
                percentage(rows.stream().filter(this::isDelayed).count(), caseCount)
        );
    }

    private List<AdminActivationAnalyticsGovernorateItemResponse> buildGovernorateBreakdown(
            List<ActivationAnalyticsRowEntity> rows,
            int totalCases
    ) {
        return groupRows(rows, ActivationAnalyticsRowEntity::getGouvernorat, "Unknown").entrySet().stream()
                .map(entry -> toGovernorateItem(entry.getKey(), entry.getValue(), totalCases))
                .sorted(Comparator.comparing(AdminActivationAnalyticsGovernorateItemResponse::caseCount).reversed()
                        .thenComparing(
                                AdminActivationAnalyticsGovernorateItemResponse::gouvernorat,
                                Comparator.nullsLast(String::compareToIgnoreCase)
                        ))
                .toList();
    }

    private AdminActivationAnalyticsGovernorateItemResponse toGovernorateItem(
            String governorate,
            List<ActivationAnalyticsRowEntity> rows,
            int totalCases
    ) {
        Map<String, Long> sttCounts = groupRows(rows, ActivationAnalyticsRowEntity::getNameStt, "Unassigned").entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));

        String topStt = sttCounts.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);

        int caseCount = rows.size();
        return new AdminActivationAnalyticsGovernorateItemResponse(
                governorate,
                caseCount,
                percentage(caseCount, totalCases),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getAssignmentBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getRdvBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getExecutionBusinessDays).toList()),
                averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getEndToEndBusinessDays).toList()),
                percentage(rows.stream().filter(this::isRdvSuccess).count(), caseCount),
                percentage(rows.stream().filter(this::isCompleted).count(), caseCount),
                percentage(rows.stream().filter(this::isDelayed).count(), caseCount),
                distinctCount(rows, ActivationAnalyticsRowEntity::getNameStt),
                topStt
        );
    }

    private List<AdminActivationAnalyticsCauseItemResponse> buildCauseRanking(
            List<ActivationAnalyticsRowEntity> rows,
            Function<ActivationAnalyticsRowEntity, String> extractor
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();

        for (ActivationAnalyticsRowEntity row : rows) {
            String rawValue = ActivationAnalyticsSupport.cleanString(extractor.apply(row));
            if (!ActivationAnalyticsSupport.hasText(rawValue)) {
                continue;
            }

            String normalized = ActivationAnalyticsSupport.normalizeLookupValue(rawValue);
            if (!ActivationAnalyticsSupport.hasText(normalized)) {
                continue;
            }

            counts.merge(normalized, 1L, Long::sum);
            labels.putIfAbsent(normalized, rawValue);
        }

        long totalCauses = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new AdminActivationAnalyticsCauseItemResponse(
                        labels.get(entry.getKey()),
                        entry.getValue().intValue(),
                        percentage(entry.getValue(), totalCauses)
                ))
                .toList();
    }

    private List<AdminActivationAnalyticsPipelineStageResponse> buildPipelineStages(List<ActivationAnalyticsRowEntity> rows) {
        return List.of(
                new AdminActivationAnalyticsPipelineStageResponse(
                        "assignment",
                        "Assignment delay",
                        averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getAssignmentBusinessDays).toList()),
                        measuredCases(rows, ActivationAnalyticsRowEntity::getAssignmentBusinessDays)
                ),
                new AdminActivationAnalyticsPipelineStageResponse(
                        "rdv",
                        "RDV delay",
                        averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getRdvBusinessDays).toList()),
                        measuredCases(rows, ActivationAnalyticsRowEntity::getRdvBusinessDays)
                ),
                new AdminActivationAnalyticsPipelineStageResponse(
                        "execution",
                        "Execution delay",
                        averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getExecutionBusinessDays).toList()),
                        measuredCases(rows, ActivationAnalyticsRowEntity::getExecutionBusinessDays)
                ),
                new AdminActivationAnalyticsPipelineStageResponse(
                        "end_to_end",
                        "End-to-end SLA",
                        averageIntegers(rows.stream().map(ActivationAnalyticsRowEntity::getEndToEndBusinessDays).toList()),
                        measuredCases(rows, ActivationAnalyticsRowEntity::getEndToEndBusinessDays)
                )
        );
    }

    private List<AdminActivationAnalyticsQualitySliceResponse> buildQualitySlices(
            List<ActivationAnalyticsRowEntity> rows,
            Function<ActivationAnalyticsRowEntity, String> labelExtractor,
            String unknownLabel
    ) {
        return groupRows(rows, labelExtractor, unknownLabel).entrySet().stream()
                .map(entry -> new AdminActivationAnalyticsQualitySliceResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        averageDoubles(entry.getValue().stream().map(ActivationAnalyticsRowEntity::getDebitDlValue).toList()),
                        averageDoubles(entry.getValue().stream().map(ActivationAnalyticsRowEntity::getDebitUlValue).toList()),
                        averageDoubles(entry.getValue().stream().map(ActivationAnalyticsRowEntity::getRsrpValue).toList()),
                        averageDoubles(entry.getValue().stream().map(ActivationAnalyticsRowEntity::getRsrqValue).toList()),
                        averageDoubles(entry.getValue().stream().map(ActivationAnalyticsRowEntity::getSinrValue).toList()),
                        averageDoubles(entry.getValue().stream().map(ActivationAnalyticsRowEntity::getMetrageCableValue).toList()),
                        averageDoubles(entry.getValue().stream().map(ActivationAnalyticsRowEntity::getNombreDePvValue).toList())
                ))
                .sorted(Comparator.comparing(AdminActivationAnalyticsQualitySliceResponse::caseCount).reversed()
                        .thenComparing(AdminActivationAnalyticsQualitySliceResponse::label, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(8)
                .toList();
    }

    private List<AdminActivationAnalyticsAlertResponse> buildAlerts(
            AdminActivationAnalyticsKpiResponse kpis,
            List<AdminActivationAnalyticsSttItemResponse> sttRanking,
            List<AdminActivationAnalyticsGovernorateItemResponse> governorateBreakdown,
            String mostDelayedStage
    ) {
        List<AdminActivationAnalyticsAlertResponse> alerts = new ArrayList<>();

        if (kpis.averageEndToEndSla() != null && kpis.averageEndToEndSla() > HIGH_AVERAGE_SLA_THRESHOLD_DAYS) {
            alerts.add(new AdminActivationAnalyticsAlertResponse(
                    "danger",
                    "High average SLA",
                    "Average end-to-end SLA is " + kpis.averageEndToEndSla()
                            + " business day(s), which is above the operational threshold."
            ));
        }

        sttRanking.stream()
                .max(Comparator.comparing(AdminActivationAnalyticsSttItemResponse::percentage))
                .filter(item -> item.percentage() >= HIGH_STT_LOAD_THRESHOLD)
                .ifPresent(item -> alerts.add(new AdminActivationAnalyticsAlertResponse(
                        "warning",
                        "Overloaded STT",
                        item.sttName() + " carries " + item.percentage() + "% of the workload."
                )));

        selectWorstGovernorate(governorateBreakdown)
                .filter(item -> item.averageEndToEndSla() != null)
                .ifPresent(item -> alerts.add(new AdminActivationAnalyticsAlertResponse(
                        "warning",
                        "Weak governorate",
                        item.gouvernorat() + " has the weakest SLA at " + item.averageEndToEndSla() + " business day(s)."
                )));

        if (kpis.abandonRate() >= HIGH_ABANDON_RATE_THRESHOLD) {
            alerts.add(new AdminActivationAnalyticsAlertResponse(
                    "warning",
                    "High abandon rate",
                    "Abandon causes affect " + kpis.abandonRate() + "% of analyzed activation cases."
            ));
        }

        if (kpis.injoignableRate() >= HIGH_INJOIGNABLE_RATE_THRESHOLD) {
            alerts.add(new AdminActivationAnalyticsAlertResponse(
                    "warning",
                    "High injoignable rate",
                    "Injoignable cases represent " + kpis.injoignableRate() + "% of the dataset."
            ));
        }

        if (ActivationAnalyticsSupport.hasText(mostDelayedStage)) {
            alerts.add(new AdminActivationAnalyticsAlertResponse(
                    "info",
                    "Pipeline bottleneck",
                    "The stage currently causing the most delay is " + mostDelayedStage + "."
            ));
        }

        return alerts;
    }

    private List<String> buildInsights(
            List<AdminActivationAnalyticsSttItemResponse> sttRanking,
            List<AdminActivationAnalyticsGovernorateItemResponse> governorateBreakdown,
            List<AdminActivationAnalyticsPipelineStageResponse> pipelineStages,
            AdminActivationAnalyticsBatchResponse latestBatch
    ) {
        List<String> insights = new ArrayList<>();

        sttRanking.stream()
                .max(Comparator.comparing(AdminActivationAnalyticsSttItemResponse::caseCount))
                .ifPresent(item -> insights.add(item.sttName() + " is the most used STT with " + item.caseCount() + " case(s)."));

        selectBestStt(sttRanking)
                .ifPresent(item -> insights.add(item.sttName()
                        + " is the best performing STT based on completion, RDV success, and SLA balance."));

        selectWorstGovernorate(governorateBreakdown)
                .ifPresent(item -> insights.add(item.gouvernorat()
                        + " is the weakest governorate with an average end-to-end SLA of "
                        + (item.averageEndToEndSla() == null ? "-" : item.averageEndToEndSla())
                        + " business day(s)."));

        pipelineStages.stream()
                .filter(stage -> stage.averageBusinessDays() != null)
                .max(Comparator.comparing(AdminActivationAnalyticsPipelineStageResponse::averageBusinessDays))
                .ifPresent(stage -> insights.add(stage.label() + " is the pipeline stage causing the highest average delay."));

        if (latestBatch != null) {
            insights.add("Latest imported batch " + latestBatch.batchId() + " came from " + latestBatch.fileName() + ".");
        }

        return insights;
    }

    private Optional<AdminActivationAnalyticsSttItemResponse> selectBestStt(List<AdminActivationAnalyticsSttItemResponse> items) {
        return items.stream().min(sttPerformanceComparator());
    }

    private Optional<AdminActivationAnalyticsSttItemResponse> selectWorstStt(List<AdminActivationAnalyticsSttItemResponse> items) {
        return items.stream().max(sttPerformanceComparator());
    }

    private Comparator<AdminActivationAnalyticsSttItemResponse> sttPerformanceComparator() {
        return Comparator
                .comparing(AdminActivationAnalyticsSttItemResponse::workCompletionRate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdminActivationAnalyticsSttItemResponse::rdvSuccessRate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdminActivationAnalyticsSttItemResponse::averageEndToEndSla, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminActivationAnalyticsSttItemResponse::delayedRate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminActivationAnalyticsSttItemResponse::caseCount, Comparator.reverseOrder())
                .thenComparing(AdminActivationAnalyticsSttItemResponse::sttName, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private Optional<AdminActivationAnalyticsGovernorateItemResponse> selectBestGovernorate(
            List<AdminActivationAnalyticsGovernorateItemResponse> items
    ) {
        return items.stream().min(Comparator
                .comparing(AdminActivationAnalyticsGovernorateItemResponse::averageEndToEndSla, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminActivationAnalyticsGovernorateItemResponse::workCompletionRate, Comparator.reverseOrder())
                .thenComparing(AdminActivationAnalyticsGovernorateItemResponse::rdvSuccessRate, Comparator.reverseOrder()));
    }

    private Optional<AdminActivationAnalyticsGovernorateItemResponse> selectWorstGovernorate(
            List<AdminActivationAnalyticsGovernorateItemResponse> items
    ) {
        return items.stream().max(Comparator
                .comparing(AdminActivationAnalyticsGovernorateItemResponse::averageEndToEndSla, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminActivationAnalyticsGovernorateItemResponse::delayedRate, Comparator.naturalOrder())
                .thenComparing(AdminActivationAnalyticsGovernorateItemResponse::caseCount, Comparator.naturalOrder()));
    }

    private Map<String, List<ActivationAnalyticsRowEntity>> groupRows(
            List<ActivationAnalyticsRowEntity> rows,
            Function<ActivationAnalyticsRowEntity, String> extractor,
            String fallbackLabel
    ) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> ActivationAnalyticsSupport.hasText(ActivationAnalyticsSupport.cleanString(extractor.apply(row)))
                                ? ActivationAnalyticsSupport.cleanString(extractor.apply(row))
                                : fallbackLabel,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private AdminActivationAnalyticsBatchResponse toBatchResponse(ActivationAnalyticsImportBatchEntity entity) {
        return new AdminActivationAnalyticsBatchResponse(
                entity.getBatchId(),
                entity.getFileName(),
                entity.getUploadedAt(),
                valueOrZero(entity.getTotalRows()),
                valueOrZero(entity.getInsertedRows()),
                valueOrZero(entity.getValidRows()),
                valueOrZero(entity.getInvalidRows()),
                valueOrZero(entity.getDuplicateRows()),
                valueOrZero(entity.getDistinctStts()),
                valueOrZero(entity.getDistinctGovernorates())
        );
    }

    private boolean isCompleted(ActivationAnalyticsRowEntity row) {
        return row.getDateFinTrvDate() != null
                || hasPositiveSignal(row.getRepTravauxStt())
                || ActivationAnalyticsSupport.containsKeyword(
                        row.getStatut(),
                        "completed",
                        "termine",
                        "terminÃ©",
                        "cloture",
                        "clÃ´ture",
                        "done"
                );
    }

    private boolean isRdvSuccess(ActivationAnalyticsRowEntity row) {
        return row.getDatePriseRdvDate() != null
                || hasPositiveSignal(row.getRepRdv())
                || ActivationAnalyticsSupport.containsKeyword(row.getRepRdv(), "rdv pris", "rdv confirme", "rdv confirmÃ©");
    }

    private boolean isInjoignable(ActivationAnalyticsRowEntity row) {
        return ActivationAnalyticsSupport.hasText(row.getCauseInjoignabilite())
                || ActivationAnalyticsSupport.containsKeyword(row.getRepRdv(), "injoignable", "unreachable", "no answer")
                || ActivationAnalyticsSupport.containsKeyword(row.getStatut(), "injoignable")
                || ActivationAnalyticsSupport.containsKeyword(row.getDetail(), "injoignable");
    }

    private boolean isAbandon(ActivationAnalyticsRowEntity row) {
        return ActivationAnalyticsSupport.hasText(row.getCauseAbandon())
                || ActivationAnalyticsSupport.containsKeyword(row.getRepRdv(), "abandon")
                || ActivationAnalyticsSupport.containsKeyword(row.getStatut(), "abandon")
                || ActivationAnalyticsSupport.containsKeyword(row.getDetail(), "abandon");
    }

    private boolean isBlocked(ActivationAnalyticsRowEntity row) {
        return ActivationAnalyticsSupport.containsKeyword(row.getStatut(), "gele", "gelÃ©", "bloque", "bloquÃ©", "blocked")
                || ActivationAnalyticsSupport.containsKeyword(row.getDetail(), "gele", "gelÃ©", "bloque", "bloquÃ©", "blocked")
                || ActivationAnalyticsSupport.containsKeyword(row.getCommentaire(), "gele", "gelÃ©", "bloque", "bloquÃ©", "blocked")
                || ActivationAnalyticsSupport.containsKeyword(row.getCmtZm(), "gele", "gelÃ©", "bloque", "bloquÃ©", "blocked")
                || ActivationAnalyticsSupport.containsKeyword(row.getCmtTrvStt(), "gele", "gelÃ©", "bloque", "bloquÃ©", "blocked");
    }

    private boolean isDelayed(ActivationAnalyticsRowEntity row) {
        if (row.getEndToEndBusinessDays() != null) {
            return row.getEndToEndBusinessDays() > END_TO_END_DELAY_THRESHOLD_DAYS;
        }

        return valueAbove(row.getAssignmentBusinessDays(), ASSIGNMENT_DELAY_THRESHOLD_DAYS)
                || valueAbove(row.getRdvBusinessDays(), RDV_DELAY_THRESHOLD_DAYS)
                || valueAbove(row.getExecutionBusinessDays(), EXECUTION_DELAY_THRESHOLD_DAYS);
    }

    private boolean hasPositiveSignal(String value) {
        return ActivationAnalyticsSupport.containsKeyword(
                value,
                "oui",
                "ok",
                "success",
                "succÃ¨s",
                "succes",
                "confirm",
                "confirme",
                "confirmÃ©",
                "favorable",
                "realise",
                "rÃ©alisÃ©",
                "pris"
        );
    }

    private boolean valueAbove(Integer value, int threshold) {
        return value != null && value > threshold;
    }

    private int distinctCount(List<ActivationAnalyticsRowEntity> rows, Function<ActivationAnalyticsRowEntity, String> extractor) {
        return (int) rows.stream()
                .map(extractor)
                .map(ActivationAnalyticsSupport::cleanString)
                .filter(ActivationAnalyticsSupport::hasText)
                .distinct()
                .count();
    }

    private int measuredCases(List<ActivationAnalyticsRowEntity> rows, Function<ActivationAnalyticsRowEntity, Integer> extractor) {
        return (int) rows.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .count();
    }

    private Double averageIntegers(List<Integer> values) {
        return ActivationAnalyticsSupport.average(values.stream()
                .filter(Objects::nonNull)
                .map(Integer::doubleValue)
                .toList());
    }

    private Double averageDoubles(List<Double> values) {
        return ActivationAnalyticsSupport.average(values);
    }

    private double percentage(long value, long total) {
        if (total <= 0) {
            return 0d;
        }
        return ActivationAnalyticsSupport.round2((value * 100d) / total);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
