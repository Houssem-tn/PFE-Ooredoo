package com.pfe.plainte_analytics.service;

import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsAlertResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsBatchResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsCauseItemResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsChartsResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsDashboardResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsGovernorateItemResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsKpiResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsPipelineStageResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsQualitySliceResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsRowListItemResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsRowsPageResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsSttItemResponse;
import com.pfe.plainte_analytics.dto.AdminPlainteAnalyticsSummaryResponse;
import com.pfe.plainte_analytics.entity.PlainteAnalyticsImportBatchEntity;
import com.pfe.plainte_analytics.entity.PlainteAnalyticsRowEntity;
import com.pfe.plainte_analytics.repository.PlainteAnalyticsImportBatchRepository;
import com.pfe.plainte_analytics.repository.PlainteAnalyticsRowRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlainteAnalyticsDashboardService {

    private static final Logger log = LoggerFactory.getLogger(PlainteAnalyticsDashboardService.class);
    private static final int ROW_PAGE_MAX_SIZE = 200;
    private static final int END_TO_END_DELAY_THRESHOLD_DAYS = 5;
    private static final int ASSIGNMENT_DELAY_THRESHOLD_DAYS = 2;
    private static final int RDV_DELAY_THRESHOLD_DAYS = 2;
    private static final int EXECUTION_DELAY_THRESHOLD_DAYS = 3;
    private static final double HIGH_AVERAGE_SLA_THRESHOLD_DAYS = 5d;
    private static final double HIGH_ABANDON_RATE_THRESHOLD = 15d;
    private static final double HIGH_INJOIGNABLE_RATE_THRESHOLD = 15d;
    private static final double HIGH_STT_LOAD_THRESHOLD = 35d;

    private final PlainteAnalyticsRowRepository rowRepository;
    private final PlainteAnalyticsImportBatchRepository batchRepository;
    private final MongoTemplate mongoTemplate;

    public AdminPlainteAnalyticsDashboardResponse getDashboard(String uploadedBy, String stt, String governorate) {
        return merge(getSummary(uploadedBy, stt, governorate), getCharts(uploadedBy, stt, governorate));
    }

    public AdminPlainteAnalyticsSummaryResponse getSummary(String uploadedBy, String stt, String governorate) {
        List<PlainteAnalyticsImportBatchEntity> batchEntities = batchRepository.findAllByUploadedByOrderByUploadedAtDesc(uploadedBy);
        return buildSummaryView(
                uploadedBy,
                null,
                stt,
                governorate,
                rowRepository.countByUploadedBy(uploadedBy),
                rowRepository.countByUploadedByAndValidRowTrue(uploadedBy),
                batchRepository.countByUploadedBy(uploadedBy),
                batchEntities,
                "live_summary"
        );
    }

    public AdminPlainteAnalyticsChartsResponse getCharts(String uploadedBy, String stt, String governorate) {
        return buildChartsView(
                uploadedBy,
                null,
                stt,
                governorate,
                batchRepository.findTopByUploadedByOrderByUploadedAtDesc(uploadedBy)
                        .map(batch -> List.of(batch))
                        .orElse(List.of()),
                "live_charts"
        );
    }

    public AdminPlainteAnalyticsRowsPageResponse getRows(
            String uploadedBy,
            String stt,
            String governorate,
            int page,
            int size
    ) {
        return getRowsPage(uploadedBy, null, stt, governorate, page, size, "live_rows");
    }

    public AdminPlainteAnalyticsDashboardResponse getSavedStudyDashboard(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        return merge(
                getSavedStudySummary(uploadedBy, batchIds, stt, governorate),
                getSavedStudyCharts(uploadedBy, batchIds, stt, governorate)
        );
    }

    public AdminPlainteAnalyticsSummaryResponse getSavedStudySummary(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        if (batchIds == null || batchIds.isEmpty()) {
            return emptySummary(0, 0, 0, null, List.of(), List.of(), List.of(), stt, governorate);
        }

        List<PlainteAnalyticsImportBatchEntity> batchEntities =
                batchRepository.findByUploadedByAndBatchIdInOrderByUploadedAtDesc(uploadedBy, batchIds);
        return buildSummaryView(
                uploadedBy,
                batchIds,
                stt,
                governorate,
                batchEntities.stream().mapToInt(batch -> valueOrZero(batch.getInsertedRows())).sum(),
                batchEntities.stream().mapToInt(batch -> valueOrZero(batch.getInsertedRows())).sum(),
                batchEntities.size(),
                batchEntities,
                "saved_summary"
        );
    }

    public AdminPlainteAnalyticsChartsResponse getSavedStudyCharts(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        if (batchIds == null || batchIds.isEmpty()) {
            return emptyCharts();
        }

        List<PlainteAnalyticsImportBatchEntity> batchEntities =
                batchRepository.findByUploadedByAndBatchIdInOrderByUploadedAtDesc(uploadedBy, batchIds);
        return buildChartsView(
                uploadedBy,
                batchIds,
                stt,
                governorate,
                batchEntities,
                "saved_charts"
        );
    }

    public AdminPlainteAnalyticsRowsPageResponse getSavedStudyRows(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate,
            int page,
            int size
    ) {
        return getRowsPage(uploadedBy, batchIds, stt, governorate, page, size, "saved_rows");
    }

    public AdminPlainteAnalyticsSummaryResponse toSummary(AdminPlainteAnalyticsDashboardResponse dashboard) {
        if (dashboard == null) {
            return emptySummary(0, 0, 0, null, List.of(), List.of(), List.of(), null, null);
        }

        return new AdminPlainteAnalyticsSummaryResponse(
                dashboard.storedRows(),
                dashboard.validStoredRows(),
                dashboard.batchCount(),
                dashboard.latestBatch(),
                dashboard.recentBatches(),
                dashboard.availableStts(),
                dashboard.availableGovernorates(),
                dashboard.selectedStt(),
                dashboard.selectedGovernorate(),
                dashboard.kpis(),
                dashboard.rdvSuccessRate(),
                dashboard.averageDebitDl(),
                dashboard.averageDebitUl(),
                dashboard.averageRsrp(),
                dashboard.averageRsrq(),
                dashboard.averageSinr(),
                dashboard.averageMetrageCable(),
                dashboard.averageNombreDePv()
        );
    }

    public AdminPlainteAnalyticsChartsResponse toCharts(AdminPlainteAnalyticsDashboardResponse dashboard) {
        if (dashboard == null) {
            return emptyCharts();
        }

        return new AdminPlainteAnalyticsChartsResponse(
                dashboard.bestStt(),
                dashboard.worstStt(),
                dashboard.bestGovernorate(),
                dashboard.worstGovernorate(),
                dashboard.mostDelayedStage(),
                dashboard.sttRanking(),
                dashboard.governorateBreakdown(),
                dashboard.topInjoignabiliteCauses(),
                dashboard.topAbandonCauses(),
                dashboard.pipelineStages(),
                dashboard.qualityByStt(),
                dashboard.qualityByGovernorate(),
                dashboard.alerts(),
                dashboard.insights()
        );
    }

    public AdminPlainteAnalyticsDashboardResponse merge(
            AdminPlainteAnalyticsSummaryResponse summary,
            AdminPlainteAnalyticsChartsResponse charts
    ) {
        AdminPlainteAnalyticsSummaryResponse safeSummary = summary == null
                ? emptySummary(0, 0, 0, null, List.of(), List.of(), List.of(), null, null)
                : summary;
        AdminPlainteAnalyticsChartsResponse safeCharts = charts == null ? emptyCharts() : charts;

        return new AdminPlainteAnalyticsDashboardResponse(
                safeSummary.storedRows(),
                safeSummary.validStoredRows(),
                safeSummary.batchCount(),
                safeSummary.latestBatch(),
                safeSummary.recentBatches(),
                safeSummary.availableStts(),
                safeSummary.availableGovernorates(),
                safeSummary.selectedStt(),
                safeSummary.selectedGovernorate(),
                safeSummary.kpis(),
                safeSummary.rdvSuccessRate(),
                safeCharts.bestStt(),
                safeCharts.worstStt(),
                safeCharts.bestGovernorate(),
                safeCharts.worstGovernorate(),
                safeCharts.mostDelayedStage(),
                safeSummary.averageDebitDl(),
                safeSummary.averageDebitUl(),
                safeSummary.averageRsrp(),
                safeSummary.averageRsrq(),
                safeSummary.averageSinr(),
                safeSummary.averageMetrageCable(),
                safeSummary.averageNombreDePv(),
                safeCharts.sttRanking(),
                safeCharts.governorateBreakdown(),
                safeCharts.topInjoignabiliteCauses(),
                safeCharts.topAbandonCauses(),
                safeCharts.pipelineStages(),
                safeCharts.qualityByStt(),
                safeCharts.qualityByGovernorate(),
                safeCharts.alerts(),
                safeCharts.insights()
        );
    }

    private AdminPlainteAnalyticsSummaryResponse buildSummaryView(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate,
            long storedRows,
            long validStoredRows,
            long batchCount,
            List<PlainteAnalyticsImportBatchEntity> batchEntities,
            String purpose
    ) {
        long totalStart = System.nanoTime();
        List<String> availableStts = loadDistinctValues(uploadedBy, batchIds, "nameStt");
        List<String> availableGovernorates = loadDistinctValues(uploadedBy, batchIds, "gouvernorat");
        String selectedStt = resolveSelection(stt, availableStts);
        String selectedGovernorate = resolveSelection(governorate, availableGovernorates);
        SummaryMetrics summaryMetrics = aggregateSummaryMetrics(uploadedBy, batchIds, selectedStt, selectedGovernorate);
        int totalCases = Math.toIntExact(summaryMetrics.totalCases());

        AdminPlainteAnalyticsBatchResponse latestBatch = batchEntities.stream()
                .findFirst()
                .map(this::toBatchResponse)
                .orElse(null);
        List<AdminPlainteAnalyticsBatchResponse> recentBatches = batchEntities.stream()
                .limit(6)
                .map(this::toBatchResponse)
                .toList();

        if (summaryMetrics.totalCases() == 0) {
            log.info(
                    "plainte_dashboard_summary purpose={} uploadedBy={} totalCases=0 queryMs={} totalMs={}",
                    purpose,
                    uploadedBy,
                    summaryMetrics.queryMs(),
                    elapsedMs(totalStart)
            );
            return emptySummary(
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

        AdminPlainteAnalyticsKpiResponse kpis = new AdminPlainteAnalyticsKpiResponse(
                totalCases,
                percentage(summaryMetrics.completedCases(), summaryMetrics.totalCases()),
                roundNullable(summaryMetrics.averageAssignmentSla()),
                roundNullable(summaryMetrics.averageRdvDelay()),
                roundNullable(summaryMetrics.averageExecutionSla()),
                roundNullable(summaryMetrics.averageEndToEndSla()),
                percentage(summaryMetrics.delayedCases(), summaryMetrics.totalCases()),
                percentage(summaryMetrics.blockedCases(), summaryMetrics.totalCases()),
                percentage(summaryMetrics.injoignableCases(), summaryMetrics.totalCases()),
                percentage(summaryMetrics.abandonCases(), summaryMetrics.totalCases()),
                summaryMetrics.distinctSttCount(),
                summaryMetrics.distinctGovernorateCount()
        );

        AdminPlainteAnalyticsSummaryResponse response = new AdminPlainteAnalyticsSummaryResponse(
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
                percentage(summaryMetrics.rdvSuccessCases(), summaryMetrics.totalCases()),
                roundNullable(summaryMetrics.averageDebitDl()),
                roundNullable(summaryMetrics.averageDebitUl()),
                roundNullable(summaryMetrics.averageRsrp()),
                roundNullable(summaryMetrics.averageRsrq()),
                roundNullable(summaryMetrics.averageSinr()),
                roundNullable(summaryMetrics.averageMetrageCable()),
                roundNullable(summaryMetrics.averageNombreDePv())
        );

        log.info(
                "plainte_dashboard_summary purpose={} uploadedBy={} totalCases={} queryMs={} totalMs={}",
                purpose,
                uploadedBy,
                summaryMetrics.totalCases(),
                summaryMetrics.queryMs(),
                elapsedMs(totalStart)
        );
        return response;
    }

    private AdminPlainteAnalyticsChartsResponse buildChartsView(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate,
            List<PlainteAnalyticsImportBatchEntity> batchEntities,
            String purpose
    ) {
        long totalStart = System.nanoTime();
        SummaryMetrics summaryMetrics = aggregateSummaryMetrics(uploadedBy, batchIds, stt, governorate);
        if (summaryMetrics.totalCases() == 0) {
            log.info(
                    "plainte_dashboard_charts purpose={} uploadedBy={} totalCases=0 queryMs={} totalMs={}",
                    purpose,
                    uploadedBy,
                    summaryMetrics.queryMs(),
                    elapsedMs(totalStart)
            );
            return emptyCharts();
        }
        int totalCases = Math.toIntExact(summaryMetrics.totalCases());

        GroupedQueryResult sttGroups = aggregateGroupsByField(
                uploadedBy,
                batchIds,
                stt,
                governorate,
                "nameSttNormalized",
                "nameStt"
        );
        GroupedQueryResult governorateGroups = aggregateGroupsByField(
                uploadedBy,
                batchIds,
                stt,
                governorate,
                "gouvernoratNormalized",
                "gouvernorat"
        );
        GovernorateTopSttResult governorateTopStts = aggregateGovernorateTopStts(uploadedBy, batchIds, stt, governorate);
        List<AdminPlainteAnalyticsSttItemResponse> sttRanking =
                toSttRanking(sttGroups.documents(), summaryMetrics.totalCases());
        List<AdminPlainteAnalyticsGovernorateItemResponse> governorateBreakdown =
                toGovernorateBreakdown(governorateGroups.documents(), governorateTopStts.values(), totalCases);
        TimedCauseRanking topInjoignabiliteCauses =
                aggregateCauseRanking(uploadedBy, batchIds, stt, governorate, "causeInjoignabilite");
        TimedCauseRanking topAbandonCauses =
                aggregateCauseRanking(uploadedBy, batchIds, stt, governorate, "causeAbandon");
        List<AdminPlainteAnalyticsPipelineStageResponse> pipelineStages = buildPipelineStages(summaryMetrics);
        List<AdminPlainteAnalyticsQualitySliceResponse> qualityByStt =
                toQualitySlices(sttGroups.documents(), "Unassigned");
        List<AdminPlainteAnalyticsQualitySliceResponse> qualityByGovernorate =
                toQualitySlices(governorateGroups.documents(), "Unknown");

        AdminPlainteAnalyticsKpiResponse chartKpis = new AdminPlainteAnalyticsKpiResponse(
                totalCases,
                percentage(summaryMetrics.completedCases(), summaryMetrics.totalCases()),
                roundNullable(summaryMetrics.averageAssignmentSla()),
                roundNullable(summaryMetrics.averageRdvDelay()),
                roundNullable(summaryMetrics.averageExecutionSla()),
                roundNullable(summaryMetrics.averageEndToEndSla()),
                percentage(summaryMetrics.delayedCases(), summaryMetrics.totalCases()),
                percentage(summaryMetrics.blockedCases(), summaryMetrics.totalCases()),
                percentage(summaryMetrics.injoignableCases(), summaryMetrics.totalCases()),
                percentage(summaryMetrics.abandonCases(), summaryMetrics.totalCases()),
                summaryMetrics.distinctSttCount(),
                summaryMetrics.distinctGovernorateCount()
        );

        String bestStt = selectBestStt(sttRanking).map(AdminPlainteAnalyticsSttItemResponse::sttName).orElse(null);
        String worstStt = selectWorstStt(sttRanking).map(AdminPlainteAnalyticsSttItemResponse::sttName).orElse(null);
        String bestGovernorate = selectBestGovernorate(governorateBreakdown)
                .map(AdminPlainteAnalyticsGovernorateItemResponse::gouvernorat)
                .orElse(null);
        String worstGovernorate = selectWorstGovernorate(governorateBreakdown)
                .map(AdminPlainteAnalyticsGovernorateItemResponse::gouvernorat)
                .orElse(null);
        String mostDelayedStage = pipelineStages.stream()
                .filter(stage -> stage.averageBusinessDays() != null)
                .max(Comparator.comparing(AdminPlainteAnalyticsPipelineStageResponse::averageBusinessDays))
                .map(AdminPlainteAnalyticsPipelineStageResponse::label)
                .orElse(null);

        AdminPlainteAnalyticsBatchResponse latestBatch = batchEntities.stream()
                .findFirst()
                .map(this::toBatchResponse)
                .orElse(null);
        List<AdminPlainteAnalyticsAlertResponse> alerts =
                buildAlerts(chartKpis, sttRanking, governorateBreakdown, mostDelayedStage);
        List<String> insights = buildInsights(
                sttRanking,
                governorateBreakdown,
                pipelineStages,
                latestBatch
        );

        long queryMs = summaryMetrics.queryMs()
                + sttGroups.queryMs()
                + governorateGroups.queryMs()
                + governorateTopStts.queryMs()
                + topInjoignabiliteCauses.queryMs()
                + topAbandonCauses.queryMs();

        AdminPlainteAnalyticsChartsResponse response = new AdminPlainteAnalyticsChartsResponse(
                bestStt,
                worstStt,
                bestGovernorate,
                worstGovernorate,
                mostDelayedStage,
                sttRanking,
                governorateBreakdown,
                topInjoignabiliteCauses.items(),
                topAbandonCauses.items(),
                pipelineStages,
                qualityByStt,
                qualityByGovernorate,
                alerts,
                insights
        );

        log.info(
                "plainte_dashboard_charts purpose={} uploadedBy={} totalCases={} queryMs={} totalMs={}",
                purpose,
                uploadedBy,
                summaryMetrics.totalCases(),
                queryMs,
                elapsedMs(totalStart)
        );
        return response;
    }

    private SummaryMetrics aggregateSummaryMetrics(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        long queryStart = System.nanoTime();
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(buildRowsCriteria(uploadedBy, batchIds, stt, governorate)),
                Aggregation.group()
                        .count().as("totalCases")
                        .sum(sumTrue("completed")).as("completedCases")
                        .sum(sumTrue("delayed")).as("delayedCases")
                        .sum(sumTrue("blocked")).as("blockedCases")
                        .sum(sumTrue("injoignable")).as("injoignableCases")
                        .sum(sumTrue("abandon")).as("abandonCases")
                        .sum(sumTrue("rdvSuccess")).as("rdvSuccessCases")
                        .sum(sumPresent("assignmentBusinessDays")).as("assignmentMeasuredCases")
                        .sum(sumPresent("rdvBusinessDays")).as("rdvMeasuredCases")
                        .sum(sumPresent("executionBusinessDays")).as("executionMeasuredCases")
                        .sum(sumPresent("endToEndBusinessDays")).as("endToEndMeasuredCases")
                        .avg("assignmentBusinessDays").as("averageAssignmentSla")
                        .avg("rdvBusinessDays").as("averageRdvDelay")
                        .avg("executionBusinessDays").as("averageExecutionSla")
                        .avg("endToEndBusinessDays").as("averageEndToEndSla")
                        .avg("debitDlValue").as("averageDebitDl")
                        .avg("debitUlValue").as("averageDebitUl")
                        .avg("rsrpValue").as("averageRsrp")
                        .avg("rsrqValue").as("averageRsrq")
                        .avg("sinrValue").as("averageSinr")
                        .avg("metrageCableValue").as("averageMetrageCable")
                        .avg("nombreDePvValue").as("averageNombreDePv")
                        .addToSet("nameStt").as("sttValues")
                        .addToSet("gouvernorat").as("governorateValues")
        );

        Document document = mongoTemplate.aggregate(aggregation, PlainteAnalyticsRowEntity.class, Document.class)
                .getUniqueMappedResult();
        long queryMs = elapsedMs(queryStart);
        if (document == null) {
            return SummaryMetrics.empty(queryMs);
        }

        return new SummaryMetrics(
                readLong(document, "totalCases"),
                readLong(document, "completedCases"),
                readLong(document, "delayedCases"),
                readLong(document, "blockedCases"),
                readLong(document, "injoignableCases"),
                readLong(document, "abandonCases"),
                readLong(document, "rdvSuccessCases"),
                readLong(document, "assignmentMeasuredCases"),
                readLong(document, "rdvMeasuredCases"),
                readLong(document, "executionMeasuredCases"),
                readLong(document, "endToEndMeasuredCases"),
                readDouble(document, "averageAssignmentSla"),
                readDouble(document, "averageRdvDelay"),
                readDouble(document, "averageExecutionSla"),
                readDouble(document, "averageEndToEndSla"),
                readDouble(document, "averageDebitDl"),
                readDouble(document, "averageDebitUl"),
                readDouble(document, "averageRsrp"),
                readDouble(document, "averageRsrq"),
                readDouble(document, "averageSinr"),
                readDouble(document, "averageMetrageCable"),
                readDouble(document, "averageNombreDePv"),
                sanitizeDistinctLabels(document.get("sttValues")).size(),
                sanitizeDistinctLabels(document.get("governorateValues")).size(),
                queryMs
        );
    }

    private GroupedQueryResult aggregateGroupsByField(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate,
            String normalizedField,
            String labelField
    ) {
        long queryStart = System.nanoTime();
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(buildRowsCriteria(uploadedBy, batchIds, stt, governorate)),
                Aggregation.group(normalizedField)
                        .first(labelField).as("label")
                        .count().as("caseCount")
                        .avg("assignmentBusinessDays").as("averageAssignmentSla")
                        .avg("rdvBusinessDays").as("averageRdvDelay")
                        .avg("executionBusinessDays").as("averageExecutionSla")
                        .avg("endToEndBusinessDays").as("averageEndToEndSla")
                        .avg("debitDlValue").as("averageDebitDl")
                        .avg("debitUlValue").as("averageDebitUl")
                        .avg("rsrpValue").as("averageRsrp")
                        .avg("rsrqValue").as("averageRsrq")
                        .avg("sinrValue").as("averageSinr")
                        .avg("metrageCableValue").as("averageMetrageCable")
                        .avg("nombreDePvValue").as("averageNombreDePv")
                        .sum(sumTrue("rdvSuccess")).as("rdvSuccessCases")
                        .sum(sumTrue("completed")).as("completedCases")
                        .sum(sumTrue("abandon")).as("abandonCases")
                        .sum(sumTrue("injoignable")).as("injoignableCases")
                        .sum(sumTrue("delayed")).as("delayedCases")
                        .addToSet("nameStt").as("sttValues")
        );

        List<Document> documents = mongoTemplate.aggregate(aggregation, PlainteAnalyticsRowEntity.class, Document.class)
                .getMappedResults();
        return new GroupedQueryResult(documents, elapsedMs(queryStart));
    }

    private GovernorateTopSttResult aggregateGovernorateTopStts(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        long queryStart = System.nanoTime();
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(buildRowsCriteria(uploadedBy, batchIds, stt, governorate)),
                Aggregation.group("gouvernoratNormalized", "nameSttNormalized")
                        .first("gouvernorat").as("governorate")
                        .first("nameStt").as("sttName")
                        .count().as("caseCount")
        );

        Map<String, GovernorateTopStt> ranking = new LinkedHashMap<>();
        List<Document> documents = mongoTemplate.aggregate(aggregation, PlainteAnalyticsRowEntity.class, Document.class)
                .getMappedResults();
        for (Document document : documents) {
            Document identifier = document.get("_id", Document.class);
            String governorateKey = bucketKey(
                    identifier == null ? null : PlainteAnalyticsSupport.cleanString(identifier.getString("gouvernoratNormalized")),
                    document.getString("governorate"),
                    "unknown"
            );
            String sttName = fallbackLabel(document.getString("sttName"), "Unassigned");
            long caseCount = readLong(document, "caseCount");

            GovernorateTopStt current = ranking.get(governorateKey);
            if (current == null
                    || caseCount > current.caseCount()
                    || (caseCount == current.caseCount()
                    && sttName.compareToIgnoreCase(current.sttName()) < 0)) {
                ranking.put(governorateKey, new GovernorateTopStt(sttName, caseCount));
            }
        }

        long queryMs = elapsedMs(queryStart);
        Map<String, String> result = new LinkedHashMap<>();
        ranking.forEach((key, value) -> result.put(key, value.sttName()));
        return new GovernorateTopSttResult(result, queryMs);
    }

    private TimedCauseRanking aggregateCauseRanking(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate,
            String fieldName
    ) {
        long queryStart = System.nanoTime();
        Criteria criteria = buildRowsCriteria(uploadedBy, batchIds, stt, governorate);
        criteria.and(fieldName).ne(null).ne("");

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group(fieldName)
                        .count().as("count")
                        .first(fieldName).as("label"),
                Aggregation.sort(Sort.by(Sort.Order.desc("count")))
        );

        List<Document> documents = mongoTemplate.aggregate(aggregation, PlainteAnalyticsRowEntity.class, Document.class)
                .getMappedResults();
        long totalCauses = 0;
        List<CauseAggregate> aggregates = new ArrayList<>();
        for (Document document : documents) {
            String label = PlainteAnalyticsSupport.cleanString(document.getString("label"));
            if (!PlainteAnalyticsSupport.hasText(label)) {
                continue;
            }
            long count = readLong(document, "count");
            totalCauses += count;
            aggregates.add(new CauseAggregate(label, count));
        }

        long totalCauseCount = totalCauses;
        List<AdminPlainteAnalyticsCauseItemResponse> items = aggregates.stream()
                .limit(6)
                .map(item -> new AdminPlainteAnalyticsCauseItemResponse(
                        item.label(),
                        (int) item.count(),
                        percentage(item.count(), totalCauseCount)
                ))
                .toList();
        return new TimedCauseRanking(items, elapsedMs(queryStart));
    }

    private List<String> loadDistinctValues(String uploadedBy, Collection<String> batchIds, String fieldName) {
        Query query = Query.query(buildRowsCriteria(uploadedBy, batchIds, null, null));
        List<String> values = mongoTemplate.query(PlainteAnalyticsRowEntity.class)
                .distinct(fieldName)
                .matching(query)
                .as(String.class)
                .all();

        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        for (String value : values) {
            String cleaned = PlainteAnalyticsSupport.cleanString(value);
            if (!PlainteAnalyticsSupport.hasText(cleaned)) {
                continue;
            }
            sanitized.putIfAbsent(PlainteAnalyticsSupport.normalizeLookupValue(cleaned), cleaned);
        }

        return sanitized.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<AdminPlainteAnalyticsSttItemResponse> toSttRanking(List<Document> documents, long totalCases) {
        return documents.stream()
                .map(document -> {
                    int caseCount = (int) readLong(document, "caseCount");
                    return new AdminPlainteAnalyticsSttItemResponse(
                            fallbackLabel(document.getString("label"), "Unassigned"),
                            caseCount,
                            percentage(caseCount, totalCases),
                            roundNullable(readDouble(document, "averageAssignmentSla")),
                            roundNullable(readDouble(document, "averageRdvDelay")),
                            roundNullable(readDouble(document, "averageExecutionSla")),
                            roundNullable(readDouble(document, "averageEndToEndSla")),
                            percentage(readLong(document, "rdvSuccessCases"), caseCount),
                            percentage(readLong(document, "completedCases"), caseCount),
                            percentage(readLong(document, "abandonCases"), caseCount),
                            percentage(readLong(document, "injoignableCases"), caseCount),
                            percentage(readLong(document, "delayedCases"), caseCount)
                    );
                })
                .sorted(sttPerformanceComparator())
                .toList();
    }

    private List<AdminPlainteAnalyticsGovernorateItemResponse> toGovernorateBreakdown(
            List<Document> documents,
            Map<String, String> governorateTopStts,
            long totalCases
    ) {
        return documents.stream()
                .map(document -> {
                    int caseCount = (int) readLong(document, "caseCount");
                    String label = fallbackLabel(document.getString("label"), "Unknown");
                    String key = bucketKey(
                            document.get("_id") == null ? null : document.get("_id").toString(),
                            label,
                            "unknown"
                    );
                    return new AdminPlainteAnalyticsGovernorateItemResponse(
                            label,
                            caseCount,
                            percentage(caseCount, totalCases),
                            roundNullable(readDouble(document, "averageAssignmentSla")),
                            roundNullable(readDouble(document, "averageRdvDelay")),
                            roundNullable(readDouble(document, "averageExecutionSla")),
                            roundNullable(readDouble(document, "averageEndToEndSla")),
                            percentage(readLong(document, "rdvSuccessCases"), caseCount),
                            percentage(readLong(document, "completedCases"), caseCount),
                            percentage(readLong(document, "delayedCases"), caseCount),
                            sanitizeDistinctLabels(document.get("sttValues")).size(),
                            governorateTopStts.get(key)
                    );
                })
                .sorted(Comparator.comparing(AdminPlainteAnalyticsGovernorateItemResponse::caseCount).reversed()
                        .thenComparing(
                                AdminPlainteAnalyticsGovernorateItemResponse::gouvernorat,
                                Comparator.nullsLast(String::compareToIgnoreCase)
                        ))
                .toList();
    }

    private List<AdminPlainteAnalyticsQualitySliceResponse> toQualitySlices(
            List<Document> documents,
            String fallbackLabel
    ) {
        return documents.stream()
                .map(document -> new AdminPlainteAnalyticsQualitySliceResponse(
                        fallbackLabel(document.getString("label"), fallbackLabel),
                        (int) readLong(document, "caseCount"),
                        roundNullable(readDouble(document, "averageDebitDl")),
                        roundNullable(readDouble(document, "averageDebitUl")),
                        roundNullable(readDouble(document, "averageRsrp")),
                        roundNullable(readDouble(document, "averageRsrq")),
                        roundNullable(readDouble(document, "averageSinr")),
                        roundNullable(readDouble(document, "averageMetrageCable")),
                        roundNullable(readDouble(document, "averageNombreDePv"))
                ))
                .sorted(Comparator.comparing(AdminPlainteAnalyticsQualitySliceResponse::caseCount).reversed()
                        .thenComparing(
                                AdminPlainteAnalyticsQualitySliceResponse::label,
                                Comparator.nullsLast(String::compareToIgnoreCase)
                        ))
                .limit(8)
                .toList();
    }

    private List<AdminPlainteAnalyticsPipelineStageResponse> buildPipelineStages(SummaryMetrics metrics) {
        return List.of(
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "assignment",
                        "Assignment delay",
                        roundNullable(metrics.averageAssignmentSla()),
                        (int) metrics.assignmentMeasuredCases()
                ),
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "rdv",
                        "RDV delay",
                        roundNullable(metrics.averageRdvDelay()),
                        (int) metrics.rdvMeasuredCases()
                ),
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "execution",
                        "Execution delay",
                        roundNullable(metrics.averageExecutionSla()),
                        (int) metrics.executionMeasuredCases()
                ),
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "end_to_end",
                        "End-to-end SLA",
                        roundNullable(metrics.averageEndToEndSla()),
                        (int) metrics.endToEndMeasuredCases()
                )
        );
    }

    private Criteria buildRowsCriteria(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        Criteria criteria = Criteria.where("uploadedBy").is(uploadedBy).and("validRow").is(true);
        if (batchIds != null && !batchIds.isEmpty()) {
            criteria.and("batchId").in(batchIds);
        }

        String normalizedStt = PlainteAnalyticsSupport.normalizeLookupValue(stt);
        if (PlainteAnalyticsSupport.hasText(normalizedStt)) {
            criteria.and("nameSttNormalized").is(normalizedStt);
        }

        String normalizedGovernorate = PlainteAnalyticsSupport.normalizeLookupValue(governorate);
        if (PlainteAnalyticsSupport.hasText(normalizedGovernorate)) {
            criteria.and("gouvernoratNormalized").is(normalizedGovernorate);
        }
        return criteria;
    }

    private AggregationExpression sumTrue(String fieldName) {
        return ConditionalOperators.when(Criteria.where(fieldName).is(true)).then(1).otherwise(0);
    }

    private AggregationExpression sumPresent(String fieldName) {
        return ConditionalOperators.when(Criteria.where(fieldName).ne(null)).then(1).otherwise(0);
    }

    private long readLong(Document document, String fieldName) {
        Object value = document.get(fieldName);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Double readDouble(Document document, String fieldName) {
        Object value = document.get(fieldName);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private List<String> sanitizeDistinctLabels(Object rawValues) {
        if (!(rawValues instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        for (Object rawValue : values) {
            String cleaned = rawValue == null ? null : PlainteAnalyticsSupport.cleanString(rawValue.toString());
            if (!PlainteAnalyticsSupport.hasText(cleaned)) {
                continue;
            }
            sanitized.putIfAbsent(PlainteAnalyticsSupport.normalizeLookupValue(cleaned), cleaned);
        }
        return List.copyOf(sanitized.values());
    }

    private String fallbackLabel(String value, String fallback) {
        String cleaned = PlainteAnalyticsSupport.cleanString(value);
        return PlainteAnalyticsSupport.hasText(cleaned) ? cleaned : fallback;
    }

    private String bucketKey(String normalizedValue, String label, String fallback) {
        String cleanedNormalizedValue = PlainteAnalyticsSupport.cleanString(normalizedValue);
        if (PlainteAnalyticsSupport.hasText(cleanedNormalizedValue)) {
            return cleanedNormalizedValue;
        }

        String cleanedLabel = PlainteAnalyticsSupport.cleanString(label);
        if (PlainteAnalyticsSupport.hasText(cleanedLabel)) {
            String normalizedLabel = PlainteAnalyticsSupport.normalizeLookupValue(cleanedLabel);
            if (PlainteAnalyticsSupport.hasText(normalizedLabel)) {
                return normalizedLabel;
            }
        }
        return fallback;
    }

    private Double roundNullable(Double value) {
        return value == null ? null : PlainteAnalyticsSupport.round2(value);
    }

    private AdminPlainteAnalyticsRowsPageResponse getRowsPage(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate,
            int page,
            int size,
            String purpose
    ) {
        long totalStart = System.nanoTime();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), ROW_PAGE_MAX_SIZE);

        Query countQuery = buildRowsQuery(uploadedBy, batchIds, stt, governorate);
        long countStart = System.nanoTime();
        long totalElements = mongoTemplate.count(countQuery, PlainteAnalyticsRowEntity.class);
        long countMs = elapsedMs(countStart);

        Query pageQuery = buildRowsQuery(uploadedBy, batchIds, stt, governorate);
        pageQuery.with(Sort.by(
                Sort.Order.desc("uploadedAt"),
                Sort.Order.desc("batchId"),
                Sort.Order.asc("rowNumber")
        ));
        pageQuery.skip((long) safePage * safeSize);
        pageQuery.limit(safeSize);
        pageQuery.fields()
                .include("batchId")
                .include("uploadedAt")
                .include("sourceFileName")
                .include("rowNumber")
                .include("crmCase")
                .include("gouvernorat")
                .include("nameStt")
                .include("statut")
                .include("assignmentBusinessDays")
                .include("rdvBusinessDays")
                .include("executionBusinessDays")
                .include("endToEndBusinessDays");

        long fetchStart = System.nanoTime();
        List<AdminPlainteAnalyticsRowListItemResponse> items = mongoTemplate.find(pageQuery, PlainteAnalyticsRowEntity.class).stream()
                .map(this::toRowListItem)
                .toList();
        long fetchMs = elapsedMs(fetchStart);

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        log.info(
                "plainte_dashboard_rows purpose={} uploadedBy={} page={} size={} totalElements={} countMs={} fetchMs={} totalMs={}",
                purpose,
                uploadedBy,
                safePage,
                safeSize,
                totalElements,
                countMs,
                fetchMs,
                elapsedMs(totalStart)
        );

        return new AdminPlainteAnalyticsRowsPageResponse(items, totalElements, totalPages, safePage, safeSize);
    }

    private Query buildRowsQuery(
            String uploadedBy,
            Collection<String> batchIds,
            String stt,
            String governorate
    ) {
        Query query = Query.query(Criteria.where("uploadedBy").is(uploadedBy).and("validRow").is(true));

        if (batchIds != null && !batchIds.isEmpty()) {
            query.addCriteria(Criteria.where("batchId").in(batchIds));
        }

        String normalizedStt = PlainteAnalyticsSupport.normalizeLookupValue(stt);
        if (PlainteAnalyticsSupport.hasText(normalizedStt)) {
            query.addCriteria(Criteria.where("nameSttNormalized").is(normalizedStt));
        }

        String normalizedGovernorate = PlainteAnalyticsSupport.normalizeLookupValue(governorate);
        if (PlainteAnalyticsSupport.hasText(normalizedGovernorate)) {
            query.addCriteria(Criteria.where("gouvernoratNormalized").is(normalizedGovernorate));
        }

        return query;
    }

    private AdminPlainteAnalyticsRowListItemResponse toRowListItem(PlainteAnalyticsRowEntity entity) {
        return new AdminPlainteAnalyticsRowListItemResponse(
                entity.getId(),
                entity.getBatchId(),
                entity.getUploadedAt(),
                entity.getSourceFileName(),
                entity.getRowNumber(),
                entity.getCrmCase(),
                entity.getGouvernorat(),
                entity.getNameStt(),
                entity.getStatut(),
                entity.getAssignmentBusinessDays(),
                entity.getRdvBusinessDays(),
                entity.getExecutionBusinessDays(),
                entity.getEndToEndBusinessDays()
        );
    }

    private AdminPlainteAnalyticsSummaryResponse emptySummary(
            long storedRows,
            long validStoredRows,
            long batchCount,
            AdminPlainteAnalyticsBatchResponse latestBatch,
            List<AdminPlainteAnalyticsBatchResponse> recentBatches,
            List<String> availableStts,
            List<String> availableGovernorates,
            String selectedStt,
            String selectedGovernorate
    ) {
        return new AdminPlainteAnalyticsSummaryResponse(
                storedRows,
                validStoredRows,
                batchCount,
                latestBatch,
                recentBatches,
                availableStts,
                availableGovernorates,
                selectedStt,
                selectedGovernorate,
                new AdminPlainteAnalyticsKpiResponse(0, 0d, null, null, null, null, 0d, 0d, 0d, 0d, 0, 0),
                0d,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private AdminPlainteAnalyticsChartsResponse emptyCharts() {
        return new AdminPlainteAnalyticsChartsResponse(
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
            List<PlainteAnalyticsRowEntity> rows,
            Function<PlainteAnalyticsRowEntity, String> extractor
    ) {
        Map<String, String> values = new LinkedHashMap<>();

        for (PlainteAnalyticsRowEntity row : rows) {
            String value = PlainteAnalyticsSupport.cleanString(extractor.apply(row));
            if (!PlainteAnalyticsSupport.hasText(value)) {
                continue;
            }
            values.putIfAbsent(PlainteAnalyticsSupport.normalizeLookupValue(value), value);
        }

        return values.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String resolveSelection(String requestedValue, List<String> availableValues) {
        if (!PlainteAnalyticsSupport.hasText(requestedValue)) {
            return null;
        }

        String normalizedRequested = PlainteAnalyticsSupport.normalizeLookupValue(requestedValue);
        return availableValues.stream()
                .filter(value -> Objects.equals(
                        PlainteAnalyticsSupport.normalizeLookupValue(value),
                        normalizedRequested
                ))
                .findFirst()
                .orElse(requestedValue.trim());
    }

    private boolean matchesSelection(String normalizedRowValue, String selectedValue) {
        if (!PlainteAnalyticsSupport.hasText(selectedValue)) {
            return true;
        }
        return Objects.equals(normalizedRowValue, PlainteAnalyticsSupport.normalizeLookupValue(selectedValue));
    }

    private List<AdminPlainteAnalyticsSttItemResponse> buildSttRanking(List<PlainteAnalyticsRowEntity> rows, int totalCases) {
        return groupRows(rows, PlainteAnalyticsRowEntity::getNameStt, "Unassigned").entrySet().stream()
                .map(entry -> toSttItem(entry.getKey(), entry.getValue(), totalCases))
                .sorted(sttPerformanceComparator())
                .toList();
    }

    private AdminPlainteAnalyticsSttItemResponse toSttItem(String sttName, List<PlainteAnalyticsRowEntity> rows, int totalCases) {
        int caseCount = rows.size();
        return new AdminPlainteAnalyticsSttItemResponse(
                sttName,
                caseCount,
                percentage(caseCount, totalCases),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getAssignmentBusinessDays).toList()),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getRdvBusinessDays).toList()),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getExecutionBusinessDays).toList()),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getEndToEndBusinessDays).toList()),
                percentage(rows.stream().filter(this::isRdvSuccess).count(), caseCount),
                percentage(rows.stream().filter(this::isCompleted).count(), caseCount),
                percentage(rows.stream().filter(this::isAbandon).count(), caseCount),
                percentage(rows.stream().filter(this::isInjoignable).count(), caseCount),
                percentage(rows.stream().filter(this::isDelayed).count(), caseCount)
        );
    }

    private List<AdminPlainteAnalyticsGovernorateItemResponse> buildGovernorateBreakdown(
            List<PlainteAnalyticsRowEntity> rows,
            int totalCases
    ) {
        return groupRows(rows, PlainteAnalyticsRowEntity::getGouvernorat, "Unknown").entrySet().stream()
                .map(entry -> toGovernorateItem(entry.getKey(), entry.getValue(), totalCases))
                .sorted(Comparator.comparing(AdminPlainteAnalyticsGovernorateItemResponse::caseCount).reversed()
                        .thenComparing(
                                AdminPlainteAnalyticsGovernorateItemResponse::gouvernorat,
                                Comparator.nullsLast(String::compareToIgnoreCase)
                        ))
                .toList();
    }

    private AdminPlainteAnalyticsGovernorateItemResponse toGovernorateItem(
            String governorate,
            List<PlainteAnalyticsRowEntity> rows,
            int totalCases
    ) {
        Map<String, Long> sttCounts = groupRows(rows, PlainteAnalyticsRowEntity::getNameStt, "Unassigned").entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));

        String topStt = sttCounts.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);

        int caseCount = rows.size();
        return new AdminPlainteAnalyticsGovernorateItemResponse(
                governorate,
                caseCount,
                percentage(caseCount, totalCases),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getAssignmentBusinessDays).toList()),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getRdvBusinessDays).toList()),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getExecutionBusinessDays).toList()),
                averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getEndToEndBusinessDays).toList()),
                percentage(rows.stream().filter(this::isRdvSuccess).count(), caseCount),
                percentage(rows.stream().filter(this::isCompleted).count(), caseCount),
                percentage(rows.stream().filter(this::isDelayed).count(), caseCount),
                distinctCount(rows, PlainteAnalyticsRowEntity::getNameStt),
                topStt
        );
    }

    private List<AdminPlainteAnalyticsCauseItemResponse> buildCauseRanking(
            List<PlainteAnalyticsRowEntity> rows,
            Function<PlainteAnalyticsRowEntity, String> extractor
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();

        for (PlainteAnalyticsRowEntity row : rows) {
            String rawValue = PlainteAnalyticsSupport.cleanString(extractor.apply(row));
            if (!PlainteAnalyticsSupport.hasText(rawValue)) {
                continue;
            }

            String normalized = PlainteAnalyticsSupport.normalizeLookupValue(rawValue);
            if (!PlainteAnalyticsSupport.hasText(normalized)) {
                continue;
            }

            counts.merge(normalized, 1L, Long::sum);
            labels.putIfAbsent(normalized, rawValue);
        }

        long totalCauses = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new AdminPlainteAnalyticsCauseItemResponse(
                        labels.get(entry.getKey()),
                        entry.getValue().intValue(),
                        percentage(entry.getValue(), totalCauses)
                ))
                .toList();
    }

    private List<AdminPlainteAnalyticsPipelineStageResponse> buildPipelineStages(List<PlainteAnalyticsRowEntity> rows) {
        return List.of(
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "assignment",
                        "Assignment delay",
                        averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getAssignmentBusinessDays).toList()),
                        measuredCases(rows, PlainteAnalyticsRowEntity::getAssignmentBusinessDays)
                ),
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "rdv",
                        "RDV delay",
                        averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getRdvBusinessDays).toList()),
                        measuredCases(rows, PlainteAnalyticsRowEntity::getRdvBusinessDays)
                ),
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "execution",
                        "Execution delay",
                        averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getExecutionBusinessDays).toList()),
                        measuredCases(rows, PlainteAnalyticsRowEntity::getExecutionBusinessDays)
                ),
                new AdminPlainteAnalyticsPipelineStageResponse(
                        "end_to_end",
                        "End-to-end SLA",
                        averageIntegers(rows.stream().map(PlainteAnalyticsRowEntity::getEndToEndBusinessDays).toList()),
                        measuredCases(rows, PlainteAnalyticsRowEntity::getEndToEndBusinessDays)
                )
        );
    }

    private List<AdminPlainteAnalyticsQualitySliceResponse> buildQualitySlices(
            List<PlainteAnalyticsRowEntity> rows,
            Function<PlainteAnalyticsRowEntity, String> labelExtractor,
            String unknownLabel
    ) {
        return groupRows(rows, labelExtractor, unknownLabel).entrySet().stream()
                .map(entry -> new AdminPlainteAnalyticsQualitySliceResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        averageDoubles(entry.getValue().stream().map(PlainteAnalyticsRowEntity::getDebitDlValue).toList()),
                        averageDoubles(entry.getValue().stream().map(PlainteAnalyticsRowEntity::getDebitUlValue).toList()),
                        averageDoubles(entry.getValue().stream().map(PlainteAnalyticsRowEntity::getRsrpValue).toList()),
                        averageDoubles(entry.getValue().stream().map(PlainteAnalyticsRowEntity::getRsrqValue).toList()),
                        averageDoubles(entry.getValue().stream().map(PlainteAnalyticsRowEntity::getSinrValue).toList()),
                        averageDoubles(entry.getValue().stream().map(PlainteAnalyticsRowEntity::getMetrageCableValue).toList()),
                        averageDoubles(entry.getValue().stream().map(PlainteAnalyticsRowEntity::getNombreDePvValue).toList())
                ))
                .sorted(Comparator.comparing(AdminPlainteAnalyticsQualitySliceResponse::caseCount).reversed()
                        .thenComparing(AdminPlainteAnalyticsQualitySliceResponse::label, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(8)
                .toList();
    }

    private List<AdminPlainteAnalyticsAlertResponse> buildAlerts(
            AdminPlainteAnalyticsKpiResponse kpis,
            List<AdminPlainteAnalyticsSttItemResponse> sttRanking,
            List<AdminPlainteAnalyticsGovernorateItemResponse> governorateBreakdown,
            String mostDelayedStage
    ) {
        List<AdminPlainteAnalyticsAlertResponse> alerts = new ArrayList<>();

        if (kpis.averageEndToEndSla() != null && kpis.averageEndToEndSla() > HIGH_AVERAGE_SLA_THRESHOLD_DAYS) {
            alerts.add(new AdminPlainteAnalyticsAlertResponse(
                    "danger",
                    "High average SLA",
                    "Average end-to-end SLA is " + kpis.averageEndToEndSla()
                            + " business day(s), which is above the operational threshold."
            ));
        }

        sttRanking.stream()
                .max(Comparator.comparing(AdminPlainteAnalyticsSttItemResponse::percentage))
                .filter(item -> item.percentage() >= HIGH_STT_LOAD_THRESHOLD)
                .ifPresent(item -> alerts.add(new AdminPlainteAnalyticsAlertResponse(
                        "warning",
                        "Overloaded STT",
                        item.sttName() + " carries " + item.percentage() + "% of the workload."
                )));

        selectWorstGovernorate(governorateBreakdown)
                .filter(item -> item.averageEndToEndSla() != null)
                .ifPresent(item -> alerts.add(new AdminPlainteAnalyticsAlertResponse(
                        "warning",
                        "Weak governorate",
                        item.gouvernorat() + " has the weakest SLA at " + item.averageEndToEndSla() + " business day(s)."
                )));

        if (kpis.abandonRate() >= HIGH_ABANDON_RATE_THRESHOLD) {
            alerts.add(new AdminPlainteAnalyticsAlertResponse(
                    "warning",
                    "High abandon rate",
                    "Abandon causes affect " + kpis.abandonRate() + "% of analyzed complaint cases."
            ));
        }

        if (kpis.injoignableRate() >= HIGH_INJOIGNABLE_RATE_THRESHOLD) {
            alerts.add(new AdminPlainteAnalyticsAlertResponse(
                    "warning",
                    "High injoignable rate",
                    "Injoignable cases represent " + kpis.injoignableRate() + "% of the dataset."
            ));
        }

        if (PlainteAnalyticsSupport.hasText(mostDelayedStage)) {
            alerts.add(new AdminPlainteAnalyticsAlertResponse(
                    "info",
                    "Pipeline bottleneck",
                    "The stage currently causing the most delay is " + mostDelayedStage + "."
            ));
        }

        return alerts;
    }

    private List<String> buildInsights(
            List<AdminPlainteAnalyticsSttItemResponse> sttRanking,
            List<AdminPlainteAnalyticsGovernorateItemResponse> governorateBreakdown,
            List<AdminPlainteAnalyticsPipelineStageResponse> pipelineStages,
            AdminPlainteAnalyticsBatchResponse latestBatch
    ) {
        List<String> insights = new ArrayList<>();

        sttRanking.stream()
                .max(Comparator.comparing(AdminPlainteAnalyticsSttItemResponse::caseCount))
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
                .max(Comparator.comparing(AdminPlainteAnalyticsPipelineStageResponse::averageBusinessDays))
                .ifPresent(stage -> insights.add(stage.label() + " is the pipeline stage causing the highest average delay."));

        if (latestBatch != null) {
            insights.add("Latest imported batch " + latestBatch.batchId() + " came from " + latestBatch.fileName() + ".");
        }

        return insights;
    }

    private Optional<AdminPlainteAnalyticsSttItemResponse> selectBestStt(List<AdminPlainteAnalyticsSttItemResponse> items) {
        return items.stream().min(sttPerformanceComparator());
    }

    private Optional<AdminPlainteAnalyticsSttItemResponse> selectWorstStt(List<AdminPlainteAnalyticsSttItemResponse> items) {
        return items.stream().max(sttPerformanceComparator());
    }

    private Comparator<AdminPlainteAnalyticsSttItemResponse> sttPerformanceComparator() {
        return Comparator
                .comparing(AdminPlainteAnalyticsSttItemResponse::workCompletionRate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdminPlainteAnalyticsSttItemResponse::rdvSuccessRate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdminPlainteAnalyticsSttItemResponse::averageEndToEndSla, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminPlainteAnalyticsSttItemResponse::delayedRate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminPlainteAnalyticsSttItemResponse::caseCount, Comparator.reverseOrder())
                .thenComparing(AdminPlainteAnalyticsSttItemResponse::sttName, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private Optional<AdminPlainteAnalyticsGovernorateItemResponse> selectBestGovernorate(
            List<AdminPlainteAnalyticsGovernorateItemResponse> items
    ) {
        return items.stream().min(Comparator
                .comparing(AdminPlainteAnalyticsGovernorateItemResponse::averageEndToEndSla, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminPlainteAnalyticsGovernorateItemResponse::workCompletionRate, Comparator.reverseOrder())
                .thenComparing(AdminPlainteAnalyticsGovernorateItemResponse::rdvSuccessRate, Comparator.reverseOrder()));
    }

    private Optional<AdminPlainteAnalyticsGovernorateItemResponse> selectWorstGovernorate(
            List<AdminPlainteAnalyticsGovernorateItemResponse> items
    ) {
        return items.stream().max(Comparator
                .comparing(AdminPlainteAnalyticsGovernorateItemResponse::averageEndToEndSla, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminPlainteAnalyticsGovernorateItemResponse::delayedRate, Comparator.naturalOrder())
                .thenComparing(AdminPlainteAnalyticsGovernorateItemResponse::caseCount, Comparator.naturalOrder()));
    }

    private Map<String, List<PlainteAnalyticsRowEntity>> groupRows(
            List<PlainteAnalyticsRowEntity> rows,
            Function<PlainteAnalyticsRowEntity, String> extractor,
            String fallbackLabel
    ) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> PlainteAnalyticsSupport.hasText(PlainteAnalyticsSupport.cleanString(extractor.apply(row)))
                                ? PlainteAnalyticsSupport.cleanString(extractor.apply(row))
                                : fallbackLabel,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private AdminPlainteAnalyticsBatchResponse toBatchResponse(PlainteAnalyticsImportBatchEntity entity) {
        return new AdminPlainteAnalyticsBatchResponse(
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

    private boolean isCompleted(PlainteAnalyticsRowEntity row) {
        if (row.getCompleted() != null) {
            return row.getCompleted();
        }
        return PlainteAnalyticsSupport.isCompleted(row.getStatut(), row.getRepTravauxStt(), row.getDateFinTrvDate());
    }

    private boolean isRdvSuccess(PlainteAnalyticsRowEntity row) {
        if (row.getRdvSuccess() != null) {
            return row.getRdvSuccess();
        }
        return PlainteAnalyticsSupport.isRdvSuccess(row.getRepRdv(), row.getDatePriseRdvDate());
    }

    private boolean isInjoignable(PlainteAnalyticsRowEntity row) {
        if (row.getInjoignable() != null) {
            return row.getInjoignable();
        }
        return PlainteAnalyticsSupport.isInjoignable(
                row.getCauseInjoignabilite(),
                row.getRepRdv(),
                row.getStatut(),
                row.getDetail()
        );
    }

    private boolean isAbandon(PlainteAnalyticsRowEntity row) {
        if (row.getAbandon() != null) {
            return row.getAbandon();
        }
        return PlainteAnalyticsSupport.isAbandon(
                row.getCauseAbandon(),
                row.getRepRdv(),
                row.getStatut(),
                row.getDetail()
        );
    }

    private boolean isBlocked(PlainteAnalyticsRowEntity row) {
        if (row.getBlocked() != null) {
            return row.getBlocked();
        }
        return PlainteAnalyticsSupport.isBlocked(
                row.getStatut(),
                row.getDetail(),
                row.getCommentaire(),
                row.getCmtZm(),
                row.getCmtTrvStt()
        );
    }

    private boolean isDelayed(PlainteAnalyticsRowEntity row) {
        if (row.getDelayed() != null) {
            return row.getDelayed();
        }
        if (row.getEndToEndBusinessDays() != null) {
            return row.getEndToEndBusinessDays() > END_TO_END_DELAY_THRESHOLD_DAYS;
        }
        return valueAbove(row.getAssignmentBusinessDays(), ASSIGNMENT_DELAY_THRESHOLD_DAYS)
                || valueAbove(row.getRdvBusinessDays(), RDV_DELAY_THRESHOLD_DAYS)
                || valueAbove(row.getExecutionBusinessDays(), EXECUTION_DELAY_THRESHOLD_DAYS);
    }

    private int distinctCount(List<PlainteAnalyticsRowEntity> rows, Function<PlainteAnalyticsRowEntity, String> extractor) {
        return (int) rows.stream()
                .map(extractor)
                .map(PlainteAnalyticsSupport::cleanString)
                .filter(PlainteAnalyticsSupport::hasText)
                .distinct()
                .count();
    }

    private int measuredCases(List<PlainteAnalyticsRowEntity> rows, Function<PlainteAnalyticsRowEntity, Integer> extractor) {
        return (int) rows.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .count();
    }

    private Double averageIntegers(List<Integer> values) {
        return PlainteAnalyticsSupport.average(values.stream()
                .filter(Objects::nonNull)
                .map(Integer::doubleValue)
                .toList());
    }

    private Double averageDoubles(List<Double> values) {
        return PlainteAnalyticsSupport.average(values);
    }

    private double percentage(long value, long total) {
        if (total <= 0) {
            return 0d;
        }
        return PlainteAnalyticsSupport.round2((value * 100d) / total);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean valueAbove(Integer value, int threshold) {
        return value != null && value > threshold;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record SummaryMetrics(
            long totalCases,
            long completedCases,
            long delayedCases,
            long blockedCases,
            long injoignableCases,
            long abandonCases,
            long rdvSuccessCases,
            long assignmentMeasuredCases,
            long rdvMeasuredCases,
            long executionMeasuredCases,
            long endToEndMeasuredCases,
            Double averageAssignmentSla,
            Double averageRdvDelay,
            Double averageExecutionSla,
            Double averageEndToEndSla,
            Double averageDebitDl,
            Double averageDebitUl,
            Double averageRsrp,
            Double averageRsrq,
            Double averageSinr,
            Double averageMetrageCable,
            Double averageNombreDePv,
            int distinctSttCount,
            int distinctGovernorateCount,
            long queryMs
    ) {
        static SummaryMetrics empty(long queryMs) {
            return new SummaryMetrics(
                    0L, 0L, 0L, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0L,
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    0, 0, queryMs
            );
        }
    }

    private record GroupedQueryResult(
            List<Document> documents,
            long queryMs
    ) {
    }

    private record GovernorateTopStt(
            String sttName,
            long caseCount
    ) {
    }

    private record GovernorateTopSttResult(
            Map<String, String> values,
            long queryMs
    ) {
    }

    private record CauseAggregate(
            String label,
            long count
    ) {
    }

    private record TimedCauseRanking(
            List<AdminPlainteAnalyticsCauseItemResponse> items,
            long queryMs
    ) {
    }

    private record DashboardContext(
            List<PlainteAnalyticsRowEntity> sourceRows,
            List<PlainteAnalyticsRowEntity> filteredRows,
            long storedRows,
            long validStoredRows,
            long batchCount,
            AdminPlainteAnalyticsBatchResponse latestBatch,
            List<AdminPlainteAnalyticsBatchResponse> recentBatches,
            List<String> availableStts,
            List<String> availableGovernorates,
            String selectedStt,
            String selectedGovernorate
    ) {
    }
}
