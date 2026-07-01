package com.pfe.coverage.service;

import com.pfe.coverage.dto.CoverageAnalyticsGeoJson;
import com.pfe.coverage.dto.CoverageAnalyticsGeoJson.Feature;
import com.pfe.coverage.dto.CoverageAnalyticsGeoJson.FeatureCollection;
import com.pfe.coverage.dto.CoverageAnalyticsGeoJson.Properties;
import com.pfe.merged_analytics.entity.MergedAnalyticsRowEntity;
import com.pfe.merged_analytics.entity.MergedAnalyticsStudyEntity;
import com.pfe.merged_analytics.model.MergedProcessingStatus;
import com.pfe.merged_analytics.model.MergedSourceLabel;
import com.pfe.merged_analytics.repository.MergedAnalyticsStudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Coverage map data provider backed by the merged-analytics dataset.
 *
 * <p>Coverage markers come from the most recently imported Merged Operations file
 * (collection {@code merged_case_analytics_rows}), filtered by {@code SOURCE} — e.g.
 * {@code SOURCE = MES} = activation. Rows are queried in place via {@link MongoTemplate}
 * so huge studies (65K+ rows) never load wholesale.</p>
 *
 * <p>The map uses viewport (bounding-box) loading + client-side clustering: each request
 * asks only for markers inside the visible bounds. Results are returned as a GeoJSON
 * {@link FeatureCollection} and capped server-side. When the visible bounds match more rows
 * than the cap (only happens fully zoomed out), a uniform random {@code $sample} is returned
 * so clusters stay geographically representative instead of an index-ordered slice.</p>
 */
@Service
public class CoverageAnalyticsPointService {

    private static final Logger log = LoggerFactory.getLogger(CoverageAnalyticsPointService.class);

    /** Default cap when the caller does not specify a limit. */
    private static final int DEFAULT_LIMIT = 12_000;
    /**
     * Hard ceiling regardless of the requested limit. High enough for the frontend to load the
     * whole MES set once (client-side clustering handles the rest) while still bounding payloads.
     */
    private static final int MAX_POINTS = 200_000;
    /** Cap on MSISDN search matches (a single MSISDN normally maps to one row). */
    private static final int MSISDN_SEARCH_LIMIT = 50;

    private final MergedAnalyticsStudyRepository studyRepository;
    private final MongoTemplate mongoTemplate;

    public CoverageAnalyticsPointService(
            MergedAnalyticsStudyRepository studyRepository,
            MongoTemplate mongoTemplate
    ) {
        this.studyRepository = studyRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Returns map markers for the given {@code source} (default MES) from the latest completed
     * merged-analytics study, as a GeoJSON {@link FeatureCollection}. Only rows with valid
     * coordinates are returned. When all four bounding-box parameters are supplied, results are
     * restricted to that box; optional {@code governorate} / {@code delegation} narrow further.
     */
    public FeatureCollection getPoints(
            String source,
            Double minLat, Double minLng, Double maxLat, Double maxLng,
            String governorate, String delegation,
            int limit
    ) {
        String normalizedSource = normalizeSource(source);

        Optional<MergedAnalyticsStudyEntity> latest = studyRepository
                .findFirstByProcessingStatusOrderByUploadedAtDesc(MergedProcessingStatus.COMPLETED);

        if (latest.isEmpty()) {
            log.info("coverage analytics-points: no completed merged-analytics study found");
            return FeatureCollection.of(null, normalizedSource, 0, false, List.of());
        }

        String studyId = latest.get().getStudyId();
        Criteria criteria = buildCriteria(studyId, normalizedSource,
                minLat, minLng, maxLat, maxLng, governorate, delegation);

        // Count uses its own (unlimited) query so the cap below never affects the total.
        long total = mongoTemplate.count(new Query(criteria), MergedAnalyticsRowEntity.class);

        int safeLimit = Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_POINTS);
        boolean capped = total > safeLimit;

        List<MergedAnalyticsRowEntity> rows = capped
                ? sampleRows(criteria, safeLimit)
                : findRows(criteria, safeLimit);

        List<Feature> features = new ArrayList<>(rows.size());
        for (MergedAnalyticsRowEntity r : rows) {
            if (r.getLatitudeSiteValue() == null || r.getLongitudeSiteValue() == null) continue;
            features.add(toFeature(r));
        }

        log.info("coverage analytics-points: studyId={} source={} total={} returned={} capped={}",
                studyId, normalizedSource, total, features.size(), capped);

        return FeatureCollection.of(studyId, normalizedSource, total, capped, features);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Ordered fetch (whole matching set fits under the cap) — cheapest path. */
    private List<MergedAnalyticsRowEntity> findRows(Criteria criteria, int safeLimit) {
        Query fetch = new Query(criteria).limit(safeLimit);
        projectPointFields(fetch);
        return mongoTemplate.find(fetch, MergedAnalyticsRowEntity.class);
    }

    /**
     * Uniform random sample used only when the visible bounds match more rows than the cap
     * (fully zoomed out). {@code $sample} keeps zoomed-out clusters representative of the whole
     * area instead of returning an index-ordered (geographically skewed) slice.
     */
    private List<MergedAnalyticsRowEntity> sampleRows(Criteria criteria, int safeLimit) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sample(safeLimit),
                Aggregation.project(
                        "crmCase", "source",
                        "latitudeSiteValue", "longitudeSiteValue",
                        "gouvernorat", "delegation", "localite", "msisdn",
                        "debitDlValue", "debitUlValue",
                        "rsrpValue", "rsrqValue", "sinrValue")
        ).withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        return mongoTemplate
                .aggregate(agg, MergedAnalyticsRowEntity.class, MergedAnalyticsRowEntity.class)
                .getMappedResults();
    }

    private void projectPointFields(Query query) {
        query.fields()
                .include("crmCase").include("source")
                .include("latitudeSiteValue").include("longitudeSiteValue")
                .include("gouvernorat").include("delegation").include("localite")
                .include("msisdn")
                .include("debitDlValue").include("debitUlValue")
                .include("rsrpValue").include("rsrqValue").include("sinrValue");
    }

    private Criteria buildCriteria(String studyId, String normalizedSource,
                                   Double minLat, Double minLng, Double maxLat, Double maxLng,
                                   String governorate, String delegation) {
        List<Criteria> ands = new ArrayList<>();
        ands.add(Criteria.where("studyId").is(studyId));
        ands.add(Criteria.where("source").is(normalizedSource));

        // gte/lte against a numeric range naturally excludes missing (null) coordinates,
        // so the bounding-box branch already enforces "valid coordinates only".
        boolean hasLatBox = minLat != null && maxLat != null;
        boolean hasLngBox = minLng != null && maxLng != null;

        ands.add(hasLatBox
                ? Criteria.where("latitudeSiteValue").gte(minLat).lte(maxLat)
                : Criteria.where("latitudeSiteValue").ne(null));
        ands.add(hasLngBox
                ? Criteria.where("longitudeSiteValue").gte(minLng).lte(maxLng)
                : Criteria.where("longitudeSiteValue").ne(null));

        if (governorate != null && !governorate.isBlank()) {
            ands.add(Criteria.where("gouvernorat")
                    .regex("^" + Pattern.quote(governorate.trim()) + "$", "i"));
        }
        if (delegation != null && !delegation.isBlank()) {
            ands.add(Criteria.where("delegation")
                    .regex("^" + Pattern.quote(delegation.trim()) + "$", "i"));
        }

        return new Criteria().andOperator(ands.toArray(new Criteria[0]));
    }

    private String normalizeSource(String source) {
        String normalized = MergedSourceLabel.normalizeSource(source);
        if (normalized != null) return normalized;
        // Unknown/blank source: fall back to a sane uppercase token (defaults to MES).
        if (source == null || source.isBlank()) return "MES";
        return source.trim().toUpperCase(Locale.ROOT);
    }

    private Feature toFeature(MergedAnalyticsRowEntity r) {
        return Feature.point(r.getLongitudeSiteValue(), r.getLatitudeSiteValue(), toProperties(r));
    }

    private Properties toProperties(MergedAnalyticsRowEntity r) {
        return new Properties(
                r.getId(),
                r.getCrmCase(),
                r.getSource(),
                r.getLatitudeSiteValue(),
                r.getLongitudeSiteValue(),
                r.getGouvernorat(),
                r.getDelegation(),
                r.getLocalite(),
                r.getMsisdn(),
                r.getDebitDlValue(),
                r.getDebitUlValue(),
                r.getRsrpValue(),
                r.getRsrqValue(),
                r.getSinrValue()
        );
    }

    /**
     * Returns MES analytics rows matching {@code msisdn} (exact) with valid coordinates, from the
     * latest completed study, so the map can locate/highlight a case. Bounded by
     * {@link #MSISDN_SEARCH_LIMIT}; empty list when nothing matches or no study exists.
     */
    public List<Properties> searchByMsisdn(String source, String msisdn) {
        if (msisdn == null || msisdn.isBlank()) return List.of();

        String normalizedSource = normalizeSource(source);
        Optional<MergedAnalyticsStudyEntity> latest = studyRepository
                .findFirstByProcessingStatusOrderByUploadedAtDesc(MergedProcessingStatus.COMPLETED);
        if (latest.isEmpty()) return List.of();

        String studyId = latest.get().getStudyId();
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("studyId").is(studyId),
                Criteria.where("source").is(normalizedSource),
                Criteria.where("msisdn").is(msisdn.trim()),
                Criteria.where("latitudeSiteValue").ne(null),
                Criteria.where("longitudeSiteValue").ne(null)
        );

        Query query = new Query(criteria).limit(MSISDN_SEARCH_LIMIT);
        projectPointFields(query);
        List<MergedAnalyticsRowEntity> rows = mongoTemplate.find(query, MergedAnalyticsRowEntity.class);

        List<Properties> results = new ArrayList<>(rows.size());
        for (MergedAnalyticsRowEntity r : rows) {
            if (r.getLatitudeSiteValue() == null || r.getLongitudeSiteValue() == null) continue;
            results.add(toProperties(r));
        }
        log.info("coverage msisdn-search: studyId={} source={} msisdn={} matches={}",
                studyId, normalizedSource, msisdn.trim(), results.size());
        return results;
    }
}
