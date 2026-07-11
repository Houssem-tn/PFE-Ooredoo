package com.pfe.case_management.service;

import com.pfe.case_management.dto.ZmPerformanceGovernorateItemResponse;
import com.pfe.case_management.dto.ZmPerformanceGovernorateSttItemResponse;
import com.pfe.case_management.dto.ZmPerformanceStudyResponse;
import com.pfe.case_management.dto.ZmPerformanceSttBreakdownItemResponse;
import com.pfe.case_management.entity.ImportedTodoEntity;
import com.pfe.case_management.entity.SavedZmPerformanceStudyEntity;
import com.pfe.case_management.model.ImportedTodoSourceType;
import com.pfe.case_management.repository.SavedZmPerformanceStudyRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZmPerformanceAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(ZmPerformanceAnalyticsService.class);

    private final MongoTemplate mongoTemplate;
    private final SavedZmPerformanceStudyRepository savedZmPerformanceStudyRepository;

    public ZmPerformanceStudyResponse getStudy(
            String uploadedBy,
            String zoneManager,
            String governorate,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo
    ) {
        return loadStudy(
                "live",
                uploadedBy,
                ImportedTodoSourceType.ADMIN_PERFORMANCE_IMPORT,
                null,
                zoneManager,
                governorate,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo
        );
    }

    public ZmPerformanceStudyResponse getSavedStudy(
            String uploadedBy,
            String savedStudyId,
            String zoneManager,
            String governorate,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo
    ) {
        if (!TodoAnalyticsSupport.hasText(savedStudyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved study id is required");
        }

        resolveOwnedSavedStudy(savedStudyId, uploadedBy);
        return loadStudy(
                "saved",
                uploadedBy,
                ImportedTodoSourceType.SAVED_ADMIN_PERFORMANCE_IMPORT,
                savedStudyId.trim(),
                zoneManager,
                governorate,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo
        );
    }

    private ZmPerformanceStudyResponse loadStudy(
            String purpose,
            String uploadedBy,
            ImportedTodoSourceType sourceType,
            String savedStudyId,
            String zoneManager,
            String governorate,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo
    ) {
        long totalStart = System.nanoTime();
        validateDateRange("Open Date", openDateFrom, openDateTo);
        validateDateRange("Affectation Date", affectationDateFrom, affectationDateTo);

        long optionsStart = System.nanoTime();
        List<String> availableZoneManagers = loadDistinctValues(
                uploadedBy,
                sourceType,
                savedStudyId,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo,
                null,
                "zoneManager"
        );
        long optionsQueryMs = elapsedMs(optionsStart);

        if (availableZoneManagers.isEmpty()) {
            log.info(
                    "todo_admin_analytics purpose={} uploadedBy={} totalCases=0 optionsQueryMs={} totalMs={}",
                    purpose,
                    uploadedBy,
                    optionsQueryMs,
                    elapsedMs(totalStart)
            );
            return emptyStudy();
        }

        String selectedZoneManager = resolveOptionalSelection(zoneManager, availableZoneManagers);
        String normalizedSelectedZoneManager = TodoAnalyticsSupport.normalizeLookupValue(selectedZoneManager);

        long governorateOptionsStart = System.nanoTime();
        List<String> availableGovernorates = loadDistinctValues(
                uploadedBy,
                sourceType,
                savedStudyId,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo,
                normalizedSelectedZoneManager,
                "gouvernorat"
        );
        long governorateOptionsQueryMs = elapsedMs(governorateOptionsStart);

        String selectedGovernorate = TodoAnalyticsSupport.hasText(governorate)
                ? resolveOptionalGovernorateSelection(governorate, availableGovernorates)
                : null;
        String normalizedSelectedGovernorate = normalizeGovernorateSelection(selectedGovernorate);

        SummaryMetrics summary = aggregateSummary(
                uploadedBy,
                sourceType,
                savedStudyId,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo,
                normalizedSelectedZoneManager,
                normalizedSelectedGovernorate
        );
        TimedItems<ZmPerformanceSttBreakdownItemResponse> sttBreakdown = aggregateSttBreakdown(
                uploadedBy,
                sourceType,
                savedStudyId,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo,
                normalizedSelectedZoneManager,
                normalizedSelectedGovernorate,
                summary.totalCases()
        );
        TimedItems<ZmPerformanceGovernorateItemResponse> governorateBreakdown = aggregateGovernorateBreakdown(
                uploadedBy,
                sourceType,
                savedStudyId,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo,
                normalizedSelectedZoneManager,
                normalizedSelectedGovernorate,
                summary.totalCases()
        );

        List<ZmPerformanceSttBreakdownItemResponse> sttItems = sttBreakdown.items();
        List<ZmPerformanceGovernorateItemResponse> governorateItems = governorateBreakdown.items();
        ZmPerformanceSttBreakdownItemResponse topStt = sttItems.isEmpty() ? null : sttItems.get(0);
        ZmPerformanceGovernorateItemResponse topGovernorate = governorateItems.isEmpty() ? null : governorateItems.get(0);
        ZmPerformanceGovernorateItemResponse bestGovernorate = governorateItems.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .min(Comparator.comparingDouble(ZmPerformanceGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);
        ZmPerformanceGovernorateItemResponse worstGovernorate = governorateItems.stream()
                .filter(item -> item.averageSlaBusinessDays() != null)
                .max(Comparator.comparingDouble(ZmPerformanceGovernorateItemResponse::averageSlaBusinessDays))
                .orElse(null);

        ZmPerformanceStudyResponse response = new ZmPerformanceStudyResponse(
                selectedZoneManager,
                selectedGovernorate,
                availableZoneManagers,
                availableGovernorates,
                summary.totalCases(),
                summary.rowsWithSla(),
                roundNullable(summary.averageSlaBusinessDays(), 0d),
                roundNullable(summary.minSlaBusinessDays()),
                roundNullable(summary.maxSlaBusinessDays()),
                sttItems.size(),
                governorateItems.size(),
                topStt != null ? topStt.sttName() : null,
                topStt != null ? topStt.caseCount() : 0L,
                topGovernorate != null ? topGovernorate.gouvernorat() : null,
                topGovernorate != null ? topGovernorate.caseCount() : 0L,
                bestGovernorate != null ? bestGovernorate.gouvernorat() : null,
                bestGovernorate != null ? bestGovernorate.averageSlaBusinessDays() : null,
                worstGovernorate != null ? worstGovernorate.gouvernorat() : null,
                worstGovernorate != null ? worstGovernorate.averageSlaBusinessDays() : null,
                buildInsights(
                        selectedZoneManager,
                        summary.totalCases(),
                        summary.rowsWithSla(),
                        topStt,
                        topGovernorate,
                        worstGovernorate
                ),
                sttItems,
                governorateItems
        );

        log.info(
                "todo_admin_analytics purpose={} uploadedBy={} totalCases={} zoneManagers={} governorates={} optionsQueryMs={} governorateOptionsQueryMs={} summaryQueryMs={} sttQueryMs={} governorateQueryMs={} totalMs={}",
                purpose,
                uploadedBy,
                summary.totalCases(),
                availableZoneManagers.size(),
                availableGovernorates.size(),
                optionsQueryMs,
                governorateOptionsQueryMs,
                summary.queryMs(),
                sttBreakdown.queryMs(),
                governorateBreakdown.queryMs(),
                elapsedMs(totalStart)
        );
        return response;
    }

    private SummaryMetrics aggregateSummary(
            String uploadedBy,
            ImportedTodoSourceType sourceType,
            String savedStudyId,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo,
            String zoneManagerNormalized,
            String governorateNormalized
    ) {
        long queryStart = System.nanoTime();
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(buildRowsCriteria(
                        uploadedBy,
                        sourceType,
                        savedStudyId,
                        openDateFrom,
                        openDateTo,
                        affectationDateFrom,
                        affectationDateTo,
                        zoneManagerNormalized,
                        governorateNormalized
                )),
                Aggregation.group()
                        .count().as("totalCases")
                        .sum(sumPresent("slaBusinessDays")).as("rowsWithSla")
                        .avg("slaBusinessDays").as("averageSlaBusinessDays")
                        .min("slaBusinessDays").as("minSlaBusinessDays")
                        .max("slaBusinessDays").as("maxSlaBusinessDays")
        );

        Document document = mongoTemplate.aggregate(aggregation, ImportedTodoEntity.class, Document.class)
                .getUniqueMappedResult();
        long queryMs = elapsedMs(queryStart);
        if (document == null) {
            return new SummaryMetrics(0L, 0L, null, null, null, queryMs);
        }

        return new SummaryMetrics(
                readLong(document, "totalCases"),
                readLong(document, "rowsWithSla"),
                readDouble(document, "averageSlaBusinessDays"),
                readDouble(document, "minSlaBusinessDays"),
                readDouble(document, "maxSlaBusinessDays"),
                queryMs
        );
    }

    private TimedItems<ZmPerformanceSttBreakdownItemResponse> aggregateSttBreakdown(
            String uploadedBy,
            ImportedTodoSourceType sourceType,
            String savedStudyId,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo,
            String zoneManagerNormalized,
            String governorateNormalized,
            long totalCases
    ) {
        long queryStart = System.nanoTime();
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(buildRowsCriteria(
                        uploadedBy,
                        sourceType,
                        savedStudyId,
                        openDateFrom,
                        openDateTo,
                        affectationDateFrom,
                        affectationDateTo,
                        zoneManagerNormalized,
                        governorateNormalized
                )),
                Aggregation.group("sttNormalized")
                        .first("stt").as("label")
                        .count().as("caseCount")
                        .avg("slaBusinessDays").as("averageSlaBusinessDays")
        );

        List<ZmPerformanceSttBreakdownItemResponse> items = mongoTemplate.aggregate(aggregation, ImportedTodoEntity.class, Document.class)
                .getMappedResults()
                .stream()
                .map(document -> new ZmPerformanceSttBreakdownItemResponse(
                        fallbackLabel(document.getString("label"), "Unassigned"),
                        readLong(document, "caseCount"),
                        percentage(readLong(document, "caseCount"), totalCases),
                        roundNullable(readDouble(document, "averageSlaBusinessDays"))
                ))
                .sorted(Comparator
                        .comparingLong(ZmPerformanceSttBreakdownItemResponse::caseCount).reversed()
                        .thenComparing(item -> item.sttName() == null ? "" : item.sttName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new TimedItems<>(items, elapsedMs(queryStart));
    }

    private TimedItems<ZmPerformanceGovernorateItemResponse> aggregateGovernorateBreakdown(
            String uploadedBy,
            ImportedTodoSourceType sourceType,
            String savedStudyId,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo,
            String zoneManagerNormalized,
            String governorateNormalized,
            long totalCases
    ) {
        long queryStart = System.nanoTime();
        Criteria criteria = buildRowsCriteria(
                uploadedBy,
                sourceType,
                savedStudyId,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo,
                zoneManagerNormalized,
                governorateNormalized
        );

        Aggregation byGovernorate = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("gouvernoratNormalized")
                        .first("gouvernorat").as("label")
                        .count().as("caseCount")
                        .avg("slaBusinessDays").as("averageSlaBusinessDays")
        );
        Aggregation byGovernorateAndStt = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("gouvernoratNormalized", "sttNormalized")
                        .first("gouvernorat").as("gouvernorat")
                        .first("stt").as("sttName")
                        .count().as("caseCount")
                        .avg("slaBusinessDays").as("averageSlaBusinessDays")
        );

        List<Document> governorateDocuments = mongoTemplate.aggregate(byGovernorate, ImportedTodoEntity.class, Document.class)
                .getMappedResults();
        List<Document> governorateSttDocuments = mongoTemplate.aggregate(byGovernorateAndStt, ImportedTodoEntity.class, Document.class)
                .getMappedResults();

        Map<String, List<GovernorateSttAggregate>> sttByGovernorate = new LinkedHashMap<>();
        for (Document document : governorateSttDocuments) {
            Document identifier = document.get("_id", Document.class);
            String governorateKey = bucketKey(
                    identifier == null ? null : identifier.getString("gouvernoratNormalized"),
                    document.getString("gouvernorat"),
                    "unknown"
            );
            sttByGovernorate.computeIfAbsent(governorateKey, ignored -> new ArrayList<>())
                    .add(new GovernorateSttAggregate(
                            fallbackLabel(document.getString("sttName"), "Unassigned"),
                            readLong(document, "caseCount"),
                            roundNullable(readDouble(document, "averageSlaBusinessDays"))
                    ));
        }

        List<ZmPerformanceGovernorateItemResponse> items = new ArrayList<>();
        for (Document document : governorateDocuments) {
            String label = fallbackLabel(document.getString("label"), "Unknown");
            String governorateKey = bucketKey(document.getString("_id"), label, "unknown");
            long caseCount = readLong(document, "caseCount");
            List<GovernorateSttAggregate> sttAggregates = sttByGovernorate.getOrDefault(governorateKey, List.of());

            List<ZmPerformanceGovernorateSttItemResponse> sttBreakdown = sttAggregates.stream()
                    .sorted(Comparator
                            .comparingLong(GovernorateSttAggregate::caseCount).reversed()
                            .thenComparing(GovernorateSttAggregate::sttName, String.CASE_INSENSITIVE_ORDER))
                    .map(item -> new ZmPerformanceGovernorateSttItemResponse(
                            item.sttName(),
                            item.caseCount(),
                            percentage(item.caseCount(), caseCount),
                            item.averageSlaBusinessDays()
                    ))
                    .toList();

            GovernorateSttAggregate topStt = sttAggregates.stream()
                    .max(Comparator
                            .comparingLong(GovernorateSttAggregate::caseCount)
                            .thenComparing(GovernorateSttAggregate::sttName, String.CASE_INSENSITIVE_ORDER))
                    .orElse(null);

            items.add(new ZmPerformanceGovernorateItemResponse(
                    label,
                    caseCount,
                    percentage(caseCount, totalCases),
                    roundNullable(readDouble(document, "averageSlaBusinessDays")),
                    sttBreakdown.size(),
                    topStt != null ? topStt.sttName() : null,
                    topStt != null ? topStt.caseCount() : 0L,
                    sttBreakdown
            ));
        }

        items.sort(Comparator
                .comparingLong(ZmPerformanceGovernorateItemResponse::caseCount).reversed()
                .thenComparing(item -> item.gouvernorat() == null ? "" : item.gouvernorat(), String.CASE_INSENSITIVE_ORDER));
        return new TimedItems<>(List.copyOf(items), elapsedMs(queryStart));
    }

    private List<String> buildInsights(
            String selectedZoneManager,
            long totalCases,
            long rowsWithSla,
            ZmPerformanceSttBreakdownItemResponse topStt,
            ZmPerformanceGovernorateItemResponse topGovernorate,
            ZmPerformanceGovernorateItemResponse worstGovernorate
    ) {
        List<String> insights = new ArrayList<>();

        if (selectedZoneManager != null) {
            insights.add(selectedZoneManager + " has " + totalCases + " imported case(s) in the current study scope.");
        } else {
            insights.add("All Zone Managers combined have " + totalCases + " imported case(s) in the current study scope.");
        }

        if (topStt != null) {
            insights.add("Top STT by volume is " + topStt.sttName() + " with " + topStt.caseCount() + " case(s), representing " + topStt.percentage() + "% of the workload.");
        }

        if (topGovernorate != null) {
            insights.add("Highest case volume is in " + topGovernorate.gouvernorat() + " with " + topGovernorate.caseCount() + " case(s).");
        }

        if (worstGovernorate != null && worstGovernorate.averageSlaBusinessDays() != null) {
            insights.add("Slowest governorate on SLA is " + worstGovernorate.gouvernorat() + " with an average of " + worstGovernorate.averageSlaBusinessDays() + " business day(s).");
        }

        if (rowsWithSla == 0) {
            insights.add("No valid SLA dates were found in the current scope, so SLA averages could not be computed.");
        }

        return insights;
    }

    private ZmPerformanceStudyResponse emptyStudy() {
        return new ZmPerformanceStudyResponse(
                null,
                null,
                List.of(),
                List.of(),
                0L,
                0L,
                0d,
                null,
                null,
                0L,
                0L,
                null,
                0L,
                null,
                0L,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private List<String> loadDistinctValues(
            String uploadedBy,
            ImportedTodoSourceType sourceType,
            String savedStudyId,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo,
            String zoneManagerNormalized,
            String fieldName
    ) {
        Query query = Query.query(buildRowsCriteria(
                uploadedBy,
                sourceType,
                savedStudyId,
                openDateFrom,
                openDateTo,
                affectationDateFrom,
                affectationDateTo,
                zoneManagerNormalized,
                null
        ));

        List<String> values = mongoTemplate.query(ImportedTodoEntity.class)
                .distinct(fieldName)
                .matching(query)
                .as(String.class)
                .all();

        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        for (String value : values) {
            String display = "gouvernorat".equals(fieldName)
                    ? TodoAnalyticsSupport.canonicalGovernorate(value)
                    : TodoAnalyticsSupport.cleanString(value);
            String normalized = "gouvernorat".equals(fieldName)
                    ? normalizeGovernorateSelection(display)
                    : TodoAnalyticsSupport.normalizeLookupValue(display);
            if (!TodoAnalyticsSupport.hasText(normalized) || sanitized.containsKey(normalized)) {
                continue;
            }
            sanitized.put(normalized, display);
        }

        return sanitized.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Criteria buildRowsCriteria(
            String uploadedBy,
            ImportedTodoSourceType sourceType,
            String savedStudyId,
            LocalDate openDateFrom,
            LocalDate openDateTo,
            LocalDate affectationDateFrom,
            LocalDate affectationDateTo,
            String zoneManagerNormalized,
            String governorateNormalized
    ) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("sourceType").is(sourceType));
        criteria.add(Criteria.where("uploadedBy").is(uploadedBy));
        criteria.add(Criteria.where("validRow").is(true));

        if (TodoAnalyticsSupport.hasText(savedStudyId)) {
            criteria.add(Criteria.where("savedStudyId").is(savedStudyId.trim()));
        }

        if (openDateFrom != null || openDateTo != null) {
            Criteria openDateCriteria = Criteria.where("openDateSurTimosDate");
            if (openDateFrom != null) {
                openDateCriteria = openDateCriteria.gte(openDateFrom);
            }
            if (openDateTo != null) {
                openDateCriteria = openDateCriteria.lte(openDateTo);
            }
            criteria.add(openDateCriteria);
        }

        if (affectationDateFrom != null || affectationDateTo != null) {
            Criteria affectationDateCriteria = Criteria.where("dateAffectationSttDate");
            if (affectationDateFrom != null) {
                affectationDateCriteria = affectationDateCriteria.gte(affectationDateFrom);
            }
            if (affectationDateTo != null) {
                affectationDateCriteria = affectationDateCriteria.lte(affectationDateTo);
            }
            criteria.add(affectationDateCriteria);
        }

        if (TodoAnalyticsSupport.hasText(zoneManagerNormalized)) {
            criteria.add(Criteria.where("zoneManagerNormalized").is(zoneManagerNormalized));
        }

        if (TodoAnalyticsSupport.hasText(governorateNormalized)) {
            criteria.add(Criteria.where("gouvernoratNormalized").is(governorateNormalized));
        }

        if (criteria.size() == 1) {
            return criteria.get(0);
        }

        return new Criteria().andOperator(criteria.toArray(Criteria[]::new));
    }

    private SavedZmPerformanceStudyEntity resolveOwnedSavedStudy(String savedStudyId, String uploadedBy) {
        return savedZmPerformanceStudyRepository.findByIdAndSavedBy(savedStudyId.trim(), uploadedBy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved study was not found"));
    }

    private void validateDateRange(String label, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " To date must be on or after " + label + " From date");
        }
    }

    private String resolveOptionalSelection(String requestedValue, List<String> availableValues) {
        if (!TodoAnalyticsSupport.hasText(requestedValue)) {
            return null;
        }

        String normalizedRequested = TodoAnalyticsSupport.normalizeLookupValue(requestedValue);
        if (!TodoAnalyticsSupport.hasText(normalizedRequested)) {
            return null;
        }

        return availableValues.stream()
                .filter(value -> normalizedRequested.equals(TodoAnalyticsSupport.normalizeLookupValue(value)))
                .findFirst()
                .orElse(null);
    }

    private String resolveOptionalGovernorateSelection(String requestedValue, List<String> availableValues) {
        if (!TodoAnalyticsSupport.hasText(requestedValue)) {
            return null;
        }

        String normalizedRequested = normalizeGovernorateSelection(requestedValue);
        if (!TodoAnalyticsSupport.hasText(normalizedRequested)) {
            return null;
        }

        return availableValues.stream()
                .filter(value -> normalizedRequested.equals(normalizeGovernorateSelection(value)))
                .findFirst()
                .orElse(null);
    }

    private String normalizeGovernorateSelection(String value) {
        return TodoAnalyticsSupport.normalizeLookupValue(TodoAnalyticsSupport.canonicalGovernorate(value));
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

    private Double roundNullable(Double value) {
        return roundNullable(value, null);
    }

    private Double roundNullable(Double value, Double fallback) {
        return value == null ? fallback : TodoAnalyticsSupport.round2(value);
    }

    private double percentage(long count, long total) {
        if (total <= 0) {
            return 0d;
        }

        return TodoAnalyticsSupport.round2((count * 100d) / total);
    }

    private String fallbackLabel(String value, String fallback) {
        String cleaned = TodoAnalyticsSupport.cleanString(value);
        return TodoAnalyticsSupport.hasText(cleaned) ? cleaned : fallback;
    }

    private String bucketKey(String normalizedValue, String fallbackValue, String emptyFallback) {
        String cleanedNormalized = TodoAnalyticsSupport.cleanString(normalizedValue);
        if (TodoAnalyticsSupport.hasText(cleanedNormalized)) {
            return cleanedNormalized;
        }

        String normalizedFallback = TodoAnalyticsSupport.normalizeLookupValue(fallbackValue);
        return TodoAnalyticsSupport.hasText(normalizedFallback) ? normalizedFallback : emptyFallback;
    }

    private long elapsedMs(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1_000_000d);
    }

    private record SummaryMetrics(
            long totalCases,
            long rowsWithSla,
            Double averageSlaBusinessDays,
            Double minSlaBusinessDays,
            Double maxSlaBusinessDays,
            long queryMs
    ) {
    }

    private record TimedItems<T>(
            List<T> items,
            long queryMs
    ) {
    }

    private record GovernorateSttAggregate(
            String sttName,
            long caseCount,
            Double averageSlaBusinessDays
    ) {
    }
}
