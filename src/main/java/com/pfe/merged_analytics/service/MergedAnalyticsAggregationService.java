package com.pfe.merged_analytics.service;

import com.pfe.merged_analytics.dto.MergedAnalyticsChartsResponse;
import com.pfe.merged_analytics.dto.MergedAnalyticsChartsResponse.MonthlyTrendPoint;
import com.pfe.merged_analytics.dto.MergedAnalyticsChartsResponse.TechnicalMetricsAverages;
import com.pfe.merged_analytics.dto.MergedAnalyticsFiltersResponse;
import com.pfe.merged_analytics.dto.MergedAnalyticsSummaryResponse;
import com.pfe.merged_analytics.entity.MergedAnalyticsRowEntity;
import com.pfe.merged_analytics.entity.MergedAnalyticsStudyEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MergedAnalyticsAggregationService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final MongoTemplate mongoTemplate;

    public MergedAnalyticsFiltersResponse getFilters(MergedAnalyticsStudyEntity study) {
        return new MergedAnalyticsFiltersResponse(
                orEmpty(study.getSourcesFound()),
                orEmpty(study.getZoneManagersFound()),
                orEmpty(study.getSttsFound()),
                orEmpty(study.getGovernoratesFound()),
                orEmpty(study.getStatusesFound())
        );
    }

    public MergedAnalyticsSummaryResponse getSummary(MergedAnalyticsStudyEntity study, MergedAnalyticsFilterParams params) {
        Criteria base = buildBaseCriteria(study.getStudyId(), params);

        Map<String, Long> bySource      = aggregate(base, "source");
        Map<String, Long> bySourceLabel = aggregate(base, "sourceLabel");
        Map<String, Long> byZm          = aggregate(base, "zoneManager");
        Map<String, Long> byStt         = aggregate(base, "nameStt");
        Map<String, Long> byStatus      = aggregate(base, "statut");
        Map<String, Long> byGov         = aggregate(base, "gouvernorat");

        String dateMin = study.getDateMin() != null ? study.getDateMin().toString() : null;
        String dateMax = study.getDateMax() != null ? study.getDateMax().toString() : null;

        return new MergedAnalyticsSummaryResponse(
                study.getTotalRows(),
                study.getValidRows(),
                study.getInvalidRows(),
                study.getDuplicateRows(),
                study.getWarningRows(),
                bySource,
                bySourceLabel,
                byZm,
                byStt,
                byStatus,
                byGov,
                dateMin,
                dateMax,
                study.getMissingOptionalColumns()
        );
    }

    public MergedAnalyticsChartsResponse getCharts(MergedAnalyticsStudyEntity study, MergedAnalyticsFilterParams params) {
        Criteria base = buildBaseCriteria(study.getStudyId(), params);

        Map<String, Long> bySource      = aggregate(base, "source");
        Map<String, Long> bySourceLabel = aggregate(base, "sourceLabel");
        Map<String, Long> byZm          = aggregate(base, "zoneManager");
        Map<String, Long> byStt         = aggregate(base, "nameStt");
        Map<String, Long> byStatus      = aggregate(base, "statut");
        Map<String, Long> byGov         = aggregate(base, "gouvernorat");
        Map<String, Long> bySlaCrm      = aggregate(base, "slaCrmLabel");
        Map<String, Long> bySlaTech     = aggregate(base, "slaTechLabel");

        List<MonthlyTrendPoint> trend    = getMonthlyTrendSimple(base, resolveDateBasisField(params.dateBasis()));
        TechnicalMetricsAverages metrics = getTechnicalAverages(base);

        return new MergedAnalyticsChartsResponse(
                bySource,
                bySourceLabel,
                byZm,
                byStt,
                byStatus,
                byGov,
                bySlaCrm,
                bySlaTech,
                trend,
                metrics
        );
    }

    private Criteria buildBaseCriteria(String studyId, MergedAnalyticsFilterParams params) {
        Criteria c = Criteria.where("studyId").is(studyId);

        List<String> sources = nonBlank(params.sources());
        if (!sources.isEmpty()) {
            List<String> normalized = sources.stream().map(s -> s.trim().toUpperCase()).toList();
            c = c.and("source").in(normalized);
        }

        List<String> zoneManagers = nonBlank(params.zoneManagers());
        if (!zoneManagers.isEmpty()) {
            c = c.and("zoneManagerNormalized").in(zoneManagers.stream().map(this::normalize).toList());
        }

        List<String> stts = nonBlank(params.stts());
        if (!stts.isEmpty()) {
            c = c.and("nameSttNormalized").in(stts.stream().map(this::normalize).toList());
        }

        List<String> statuses = nonBlank(params.statuses());
        if (!statuses.isEmpty()) {
            c = c.and("statut").in(statuses.stream().map(String::trim).toList());
        }

        List<String> gouvernorats = nonBlank(params.gouvernorats());
        if (!gouvernorats.isEmpty()) {
            c = c.and("gouvernoratNormalized").in(gouvernorats.stream().map(this::normalize).toList());
        }

        String dateBasisField = resolveDateBasisField(params.dateBasis());
        if (params.dateFrom() != null || params.dateTo() != null) {
            Criteria dateCriteria = Criteria.where(dateBasisField);
            if (params.dateFrom() != null) dateCriteria = dateCriteria.gte(params.dateFrom());
            if (params.dateTo() != null)   dateCriteria = dateCriteria.lte(params.dateTo());
            c = new Criteria().andOperator(c, dateCriteria);
        }

        return c;
    }

    private Map<String, Long> aggregate(Criteria base, String groupField) {
        List<AggregationOperation> ops = new ArrayList<>();
        ops.add(Aggregation.match(base));
        ops.add(Aggregation.match(Criteria.where(groupField).ne(null).ne("")));
        ops.add(Aggregation.group(groupField).count().as("count"));
        ops.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "count")));
        ops.add(Aggregation.limit(50));

        AggregationResults<Map> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops),
                MergedAnalyticsRowEntity.class,
                Map.class
        );

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Map doc : results.getMappedResults()) {
            Object key = doc.get("_id");
            Object cnt = doc.get("count");
            if (key != null && cnt != null) {
                distribution.put(key.toString(), ((Number) cnt).longValue());
            }
        }
        return distribution;
    }

    private List<MonthlyTrendPoint> getMonthlyTrendSimple(Criteria base, String dateField) {
        List<AggregationOperation> ops = new ArrayList<>();
        ops.add(Aggregation.match(base));
        ops.add(Aggregation.match(Criteria.where(dateField).ne(null)));
        ops.add(Aggregation.group(Aggregation.fields().and(dateField)).count().as("count"));
        ops.add(Aggregation.sort(Sort.by(Sort.Direction.ASC, "_id")));

        AggregationResults<Map> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops),
                MergedAnalyticsRowEntity.class,
                Map.class
        );

        Map<String, Long> monthMap = new LinkedHashMap<>();
        for (Map doc : results.getMappedResults()) {
            Object key = doc.get("_id");
            Object cnt = doc.get("count");
            if (key == null || cnt == null) continue;
            String monthKey = extractMonthKey(key);
            if (monthKey != null) {
                monthMap.merge(monthKey, ((Number) cnt).longValue(), Long::sum);
            }
        }

        List<MonthlyTrendPoint> trend = new ArrayList<>();
        monthMap.forEach((month, count) -> trend.add(new MonthlyTrendPoint(month, count)));
        return trend;
    }

    private String extractMonthKey(Object key) {
        if (key == null) return null;
        String raw = key.toString();
        return raw.length() >= 7 ? raw.substring(0, 7) : raw;
    }

    private TechnicalMetricsAverages getTechnicalAverages(Criteria base) {
        List<AggregationOperation> ops = new ArrayList<>();
        ops.add(Aggregation.match(base));
        ops.add(Aggregation.group()
                .avg("debitDlValue").as("avgDebitDl")
                .avg("debitUlValue").as("avgDebitUl")
                .avg("rsrpValue").as("avgRsrp")
                .avg("rsrqValue").as("avgRsrq")
                .avg("sinrValue").as("avgSinr")
                .avg("slaCrmValue").as("avgSlaCrm")
                .avg("slaTechValue").as("avgSlaTech"));

        AggregationResults<Map> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops),
                MergedAnalyticsRowEntity.class,
                Map.class
        );

        if (results.getMappedResults().isEmpty()) {
            return new TechnicalMetricsAverages(null, null, null, null, null, null, null);
        }

        Map doc = results.getMappedResults().get(0);
        return new TechnicalMetricsAverages(
                toDouble(doc.get("avgDebitDl")),
                toDouble(doc.get("avgDebitUl")),
                toDouble(doc.get("avgRsrp")),
                toDouble(doc.get("avgRsrq")),
                toDouble(doc.get("avgSinr")),
                toDouble(doc.get("avgSlaCrm")),
                toDouble(doc.get("avgSlaTech"))
        );
    }

    private String resolveDateBasisField(String dateBasis) {
        if (dateBasis == null || dateBasis.isBlank()) return "dateCreationCrmDate";
        return switch (dateBasis.trim()) {
            case "OPENING_DATE_SUR_TIMOS"    -> "openingDateSurTimosDate";
            case "DATE_AFFECTATION_STT"      -> "dateAffectationSttDate";
            case "DATE_FIN_TRV"              -> "dateFinTrvDate";
            case "OPEN_DATE_ACTIVATION"      -> "openDateActivationDate";
            case "date_travaux_resiliation"  -> "dateTravauxResiliationDate";
            default                          -> "dateCreationCrmDate";
        };
    }

    /** Returns items from list that are non-null and non-blank. */
    private List<String> nonBlank(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    private List<String> orEmpty(List<String> list) {
        if (list == null) return List.of();
        return list.stream().filter(s -> s != null && !s.isBlank()).sorted().toList();
    }

    private String normalize(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    public record MergedAnalyticsFilterParams(
            List<String> sources,
            List<String> zoneManagers,
            List<String> stts,
            List<String> statuses,
            List<String> gouvernorats,
            String dateBasis,
            Instant dateFrom,
            Instant dateTo
    ) {
        public static MergedAnalyticsFilterParams empty() {
            return new MergedAnalyticsFilterParams(null, null, null, null, null, null, null, null);
        }
    }
}
